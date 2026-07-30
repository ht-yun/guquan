package com.guquan.equity.service;

import com.guquan.equity.api.CompanyBrowserLookupService;
import com.guquan.equity.model.CompanyBatchAutomationTarget;
import com.guquan.equity.model.CompanyBrowserTask;
import com.guquan.equity.model.CompanyBrowserTaskRequest;
import com.guquan.equity.model.CompanyBrowserTaskStatus;
import com.guquan.equity.model.CompanyInfoSection;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.CompanySectionView;
import com.guquan.equity.provider.CompanyBrowserProperties;
import com.guquan.equity.util.CompanyNameNormalizer;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Drives the public GSXT website only after the user has completed any required
 * verification in the visible browser. It deliberately never attempts to solve
 * captchas or evade access controls.
 */
@Service
public class DefaultCompanyBrowserLookupService implements CompanyBrowserLookupService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCompanyBrowserLookupService.class);
    private static final String GSXT_URL = "https://www.gsxt.gov.cn/";
    private static final List<String> SEARCH_INPUTS = List.of(
            "input[placeholder*='企业']", "input[placeholder*='统一社会信用代码']",
            "input[placeholder*='关键字']", "input[type='search']",
            "input[name*='keyword' i]", "input[id*='keyword' i]", "input[id*='search' i]");
    private static final List<String> SEARCH_BUTTONS = List.of(
            "button:has-text('查询')", "button:has-text('搜索')",
            "input[type='submit']", "[class*='search' i] button");
    private static final Map<CompanyInfoSection, List<String>> SECTION_LABELS = sectionLabels();

    private final CompanyBrowserProperties properties;
    private final GsxtPageParser pageParser;
    private final CompanyBrowserCandidateResolver candidateResolver;
    private final CompanyBatchService batchService;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService browserExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "company-browser-worker");
        thread.setDaemon(false);
        return thread;
    });
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "company-browser-cleanup");
        thread.setDaemon(true);
        return thread;
    });
    private final Object driverLock = new Object();
    private volatile ClassLoader driverClassLoader;

    public DefaultCompanyBrowserLookupService(CompanyBrowserProperties properties, GsxtPageParser pageParser,
            CompanyBrowserCandidateResolver candidateResolver, CompanyBatchService batchService) {
        this.properties = properties;
        this.pageParser = pageParser;
        this.candidateResolver = candidateResolver;
        this.batchService = batchService;
    }

    @Override
    public synchronized CompanyBrowserTask start(CompanyBrowserTaskRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("浏览器辅助查询功能已关闭");
        }
        if (request == null || (!StringUtils.hasText(request.getJobId())
                && !StringUtils.hasText(request.getCompanyName()) && !StringUtils.hasText(request.getCreditCode()))) {
            throw new IllegalArgumentException("请至少输入企业名称、统一社会信用代码或批量任务编号");
        }
        if (sessions.values().stream().anyMatch(this::isActive)) {
            throw new IllegalStateException("已有浏览器查询正在运行，请先完成、停止或处理当前任务");
        }
        CompanyBrowserTaskRequest safeRequest = new CompanyBrowserTaskRequest();
        safeRequest.setCompanyName(trimToNull(request.getCompanyName()));
        safeRequest.setCreditCode(trimToNull(request.getCreditCode()));
        safeRequest.setJobId(trimToNull(request.getJobId()));
        String taskId = UUID.randomUUID().toString().replace("-", "");
        Session session = new Session(taskId, safeRequest);
        sessions.put(taskId, session);

        if (isBatch(session)) {
            var job = batchService.get(safeRequest.getJobId());
            synchronized (session) {
                session.task.setTotalCompanies(job.getTotalCompanies());
                session.task.setCompletedCompanies(job.getResolved());
            }
            if (!prepareNextBatchTarget(session)) {
                update(session, CompanyBrowserTaskStatus.COMPLETED, "名单中的企业已全部完成，无需打开浏览器");
                return snapshot(session);
            }
        }

        browserExecutor.execute(() -> openAndSearch(session));
        if (properties.getMaxTaskSeconds() > 0) {
            cleanupExecutor.schedule(() -> expire(taskId), properties.getMaxTaskSeconds(), TimeUnit.SECONDS);
        }
        return snapshot(session);
    }

    @Override
    public CompanyBrowserTask get(String taskId) {
        return snapshot(requireSession(taskId));
    }

    @Override
    public CompanyBrowserTask continueTask(String taskId) {
        Session session = requireSession(taskId);
        CompletableFuture<CompanyBrowserTask> result = new CompletableFuture<>();
        browserExecutor.execute(() -> {
            try {
                requireOpenPage(session);
                synchronized (session) {
                    session.waitingForVerification = false;
                }
                if (session.task.getStatus() == CompanyBrowserTaskStatus.WAITING_SELECTION) {
                    result.complete(snapshot(session));
                    return;
                }
                processCurrentPage(session);
                result.complete(snapshot(session));
            } catch (RuntimeException ex) {
                log.error("GSXT browser lookup failed for task {}", session.taskId, ex);
                fail(session, readableMessage(ex));
                result.complete(snapshot(session));
            }
        });
        try {
            return result.get(Math.max(10, properties.getTimeoutMillis() / 1000 + 8), TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("继续处理浏览器页面超时", ex);
        }
    }

    @Override
    public CompanyBrowserTask selectCandidate(String taskId, String creditCode) {
        Session session = requireSession(taskId);
        if (!StringUtils.hasText(creditCode)) {
            throw new IllegalArgumentException("请选择企业统一社会信用代码");
        }
        CompletableFuture<CompanyBrowserTask> result = new CompletableFuture<>();
        browserExecutor.execute(() -> {
            try {
                requireOpenPage(session);
                CompanyProfile selected;
                synchronized (session) {
                    selected = session.task.getCandidates().stream()
                            .filter(candidate -> creditCode.trim().equalsIgnoreCase(candidate.getCreditCode()))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("所选企业不在当前搜索结果中"));
                }
                openSelectedCandidate(session, selected);
                result.complete(snapshot(session));
            } catch (RuntimeException ex) {
                fail(session, readableMessage(ex));
                result.complete(snapshot(session));
            }
        });
        try {
            return result.get(Math.max(10, properties.getTimeoutMillis() / 1000 + 8), TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("打开所选企业详情超时", ex);
        }
    }

    @Override
    public void close(String taskId) {
        Session session = sessions.remove(taskId);
        if (session == null) {
            return;
        }
        update(session, CompanyBrowserTaskStatus.CLOSED, "浏览器查询任务已停止");
        browserExecutor.execute(() -> closeSession(session));
    }

    private void openAndSearch(Session session) {
        update(session, CompanyBrowserTaskStatus.RUNNING, "正在打开国家企业信用信息公示系统");
        try {
            ensurePlaywrightDriverResource();
            session.playwright = Playwright.create();
            Path profileDir = properties.getUserDataDir().resolve("shared");
            Files.createDirectories(profileDir);
            session.context = session.playwright.chromium().launchPersistentContext(profileDir,
                    new BrowserType.LaunchPersistentContextOptions().setHeadless(properties.isHeadless())
                            .setLocale("zh-CN").setViewportSize(1365, 900));
            session.page = session.context.pages().isEmpty()
                    ? session.context.newPage() : session.context.pages().get(0);
            session.page.setDefaultTimeout(properties.getTimeoutMillis());
            Response response = session.page.navigate(GSXT_URL,
                    new Page.NavigateOptions().setTimeout(properties.getTimeoutMillis()));
            setCurrentUrl(session, session.page.url());
            saveScreenshot(session, "opened");
            String openedText = pageText(session).toLowerCase(Locale.ROOT);
            if ((response != null && response.status() == 521) || openedText.contains("web server is down")
                    || openedText.contains("error 521")) {
                fail(session, "国家企业信用信息公示系统返回 HTTP 521，暂时无法访问，请稍后重试");
                closeSession(session);
                return;
            }
            if (isAccessBlocked(openedText)) {
                fail(session, "国家企业信用信息公示系统拒绝当前会话访问，请稍后重试或改用手工粘贴方式");
                closeSession(session);
                return;
            }
            searchCurrentCompany(session);
        } catch (Exception ex) {
            log.error("GSXT browser startup failed for task {}", session.taskId, ex);
            fail(session, readableMessage(ex));
            closeSession(session);
        }
    }

    private void searchCurrentCompany(Session session) {
        requireOpenPage(session);
        String keyword = StringUtils.hasText(session.task.getCompanyName())
                ? session.task.getCompanyName() : session.task.getCreditCode();
        if (!StringUtils.hasText(keyword)) {
            throw new IllegalStateException("当前没有可查询的企业名称或统一社会信用代码");
        }
        Locator input = firstVisible(session.page, SEARCH_INPUTS);
        if (input == null) {
            waitForVerification(session, "官网页面尚未显示搜索框，请完成验证或等待页面加载");
            return;
        }
        input.fill(keyword);
        Locator button = firstVisible(session.page, SEARCH_BUTTONS);
        if (button != null) {
            button.click();
        } else {
            input.press("Enter");
        }
        session.page.waitForTimeout(1800);
        setCurrentUrl(session, session.page.url());
        saveScreenshot(session, "searched");
        processCurrentPage(session);
    }

    private void processCurrentPage(Session session) {
        requireOpenPage(session);
        if (hasManualChallenge(session.page)) {
            waitForVerification(session, "请在浏览器中完成验证码或安全验证，系统会自动继续采集");
            return;
        }
        if (isBatch(session)) {
            processBatchPage(session);
        } else {
            extractLegacyResults(session);
        }
    }

    private void processBatchPage(Session session) {
        String body = pageText(session);
        if (isCompanyDetailPage(body) && session.selectedCandidate != null) {
            collectSelectedCompany(session);
            return;
        }
        List<CompanyProfile> parsed = pageParser.parse(body, currentParserRequest(session), "GSXT_BROWSER_AUTOMATED");
        var resolution = candidateResolver.resolve(session.task.getCompanyName(), session.task.getCreditCode(), parsed);
        synchronized (session) {
            session.task.setCandidates(resolution.candidates());
        }
        if (resolution.automaticCandidate() != null) {
            if (isCompanyDetailPage(body)) {
                session.selectedCandidate = resolution.automaticCandidate();
                collectSelectedCompany(session);
            } else {
                openSelectedCandidate(session, resolution.automaticCandidate());
            }
            return;
        }
        if (resolution.requiresSelection()) {
            update(session, CompanyBrowserTaskStatus.WAITING_SELECTION,
                    "搜索到多个可能的企业，请在系统页面选择目标企业后继续");
            return;
        }
        markCurrentAsManual(session, "官网未识别到有效企业结果，请核对名称、验证状态或改为人工处理");
    }

    private void openSelectedCandidate(Session session, CompanyProfile candidate) {
        if (hasManualChallenge(session.page)) {
            waitForVerification(session, "打开企业详情前需要完成官网验证，验证后系统会继续");
            return;
        }
        if (isCompanyDetailPage(pageText(session))) {
            session.selectedCandidate = candidate;
            collectSelectedCompany(session);
            return;
        }
        Locator link = findCandidateLink(session.page, candidate.getCompanyName());
        if (link == null) {
            synchronized (session) {
                session.task.setCandidates(List.of(candidate));
            }
            update(session, CompanyBrowserTaskStatus.WAITING_MANUAL,
                    "已识别企业但未找到详情入口，请在浏览器打开企业详情后点击“继续采集”");
            return;
        }
        link.click();
        session.page.waitForTimeout(1500);
        setCurrentUrl(session, session.page.url());
        saveScreenshot(session, "detail");
        if (hasManualChallenge(session.page)) {
            waitForVerification(session, "进入企业详情时需要完成官网验证，验证后系统会继续");
            return;
        }
        session.selectedCandidate = candidate;
        if (!isCompanyDetailPage(pageText(session))) {
            update(session, CompanyBrowserTaskStatus.WAITING_MANUAL,
                    "未确认已进入企业详情页，请在浏览器中打开目标企业后点击“继续采集”");
            return;
        }
        collectSelectedCompany(session);
    }

    private void collectSelectedCompany(Session session) {
        if (session.selectedCandidate == null || session.currentTarget == null) {
            throw new IllegalStateException("尚未确定当前企业，无法采集详情");
        }
        update(session, CompanyBrowserTaskStatus.COLLECTING,
                "正在自动采集“" + session.task.getCompanyName() + "”的公开信息");
        String basicText = session.basicText;
        CompanyInfoSection[] allSections = CompanyInfoSection.values();
        for (int index = session.nextSectionIndex; index < allSections.length; index++) {
            CompanyInfoSection section = allSections[index];
            SectionCapture capture = collectSection(session, section);
            CompanySectionView saved = batchService.saveAutomatedSection(session.task.getJobId(),
                    session.currentTarget.getCompanyId(), section, capture.text());
            synchronized (session) {
                session.sectionStatuses.put(section, saved.getStatus());
                session.task.setSectionStatuses(Map.copyOf(session.sectionStatuses));
            }
            if (section == CompanyInfoSection.BASIC) {
                basicText = capture.text();
                session.basicText = basicText;
            }
            if (capture.requiresVerification()) {
                session.nextSectionIndex = index;
                waitForVerification(session, "采集“" + sectionLabel(section) + "”时官网再次要求验证，验证后系统将从该栏目继续");
                return;
            }
            session.nextSectionIndex = index + 1;
        }
        batchService.completeAutomatedCompany(session.task.getJobId(), session.currentTarget.getCompanyId(),
                session.selectedCandidate, basicText);
        synchronized (session) {
            session.task.setCompletedCompanies(batchService.get(session.task.getJobId()).getResolved());
            session.task.setCandidates(List.of());
            session.selectedCandidate = null;
            session.currentTarget = null;
            session.basicText = null;
            session.nextSectionIndex = 0;
            session.sectionStatuses.clear();
            session.task.setSectionStatuses(Map.of());
        }
        if (!prepareNextBatchTarget(session)) {
            update(session, CompanyBrowserTaskStatus.COMPLETED, "批量企业信息采集完成，可导出结果表");
            closeSession(session);
            return;
        }
        goToSearchPage(session);
        searchCurrentCompany(session);
    }

    private SectionCapture collectSection(Session session, CompanyInfoSection section) {
        if (section != CompanyInfoSection.BASIC && !openSection(session.page, section)) {
            return new SectionCapture(sectionLabel(section) + "\n自动采集未定位到官网栏目入口，请人工复核。", false);
        }
        List<String> pages = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < properties.getMaxPagesPerSection(); pageNumber++) {
            String text = pageText(session);
            pages.add(text);
            if (hasManualChallenge(session.page)) {
                return new SectionCapture(joinSectionText(section, pages), true);
            }
            Locator next = nextPage(session.page);
            if (next == null || isDisabled(next)) {
                break;
            }
            String before = signature(text);
            next.click();
            session.page.waitForTimeout(1200);
            String after = pageText(session);
            if (before.equals(signature(after))) {
                break;
            }
        }
        return new SectionCapture(joinSectionText(section, pages), false);
    }

    private boolean openSection(Page page, CompanyInfoSection section) {
        Locator control = findClickableByText(page, SECTION_LABELS.get(section));
        if (control == null) {
            return false;
        }
        try {
            control.click();
            page.waitForTimeout(900);
            return true;
        } catch (RuntimeException ex) {
            log.debug("Unable to open GSXT section {}", section, ex);
            return false;
        }
    }

    private void extractLegacyResults(Session session) {
        String body = pageText(session);
        List<CompanyProfile> profiles = pageParser.parse(body, currentParserRequest(session));
        synchronized (session) {
            session.task.setCandidates(List.copyOf(profiles));
            session.task.setCurrentUrl(session.page.url());
        }
        if (!profiles.isEmpty()) {
            update(session, CompanyBrowserTaskStatus.FOUND, "已解析出候选企业，可保存到本地档案");
        } else if (containsNoResult(body)) {
            update(session, CompanyBrowserTaskStatus.NOT_FOUND, "官网页面明确显示没有匹配结果");
        } else {
            update(session, CompanyBrowserTaskStatus.WAITING_MANUAL,
                    "当前页面尚未识别到有效信用代码，请确认已打开结果页后继续解析");
        }
        saveScreenshot(session, "parsed");
        closeIfTerminal(session);
    }

    private void waitForVerification(Session session, String message) {
        synchronized (session) {
            session.waitingForVerification = true;
        }
        update(session, CompanyBrowserTaskStatus.WAITING_MANUAL, message);
        cleanupExecutor.schedule(() -> checkVerification(session), 2, TimeUnit.SECONDS);
    }

    private void checkVerification(Session session) {
        if (!isActive(session)) {
            return;
        }
        browserExecutor.execute(() -> {
            try {
                if (!session.waitingForVerification || session.page == null || session.page.isClosed()) {
                    return;
                }
                if (hasManualChallenge(session.page)) {
                    cleanupExecutor.schedule(() -> checkVerification(session), 2, TimeUnit.SECONDS);
                    return;
                }
                synchronized (session) {
                    session.waitingForVerification = false;
                }
                processCurrentPage(session);
            } catch (RuntimeException ex) {
                log.debug("Unable to check GSXT verification state for task {}", session.taskId, ex);
            }
        });
    }

    private boolean prepareNextBatchTarget(Session session) {
        var next = batchService.nextAutomationTarget(session.task.getJobId());
        if (next.isEmpty()) {
            return false;
        }
        CompanyBatchAutomationTarget target = next.get();
        synchronized (session) {
            session.currentTarget = target;
            session.task.setCompanyName(target.getCompanyName());
            session.task.setCreditCode(target.getCreditCode());
            session.task.setCurrentCompanyId(target.getCompanyId());
            session.task.setCandidates(List.of());
            session.task.setSectionStatuses(Map.of());
            session.task.setMessage("准备查询：" + target.getCompanyName());
        }
        return true;
    }

    private void markCurrentAsManual(Session session, String message) {
        if (session.currentTarget != null) {
            batchService.markAutomationIssue(session.task.getJobId(), session.currentTarget.getCompanyId(), message);
        }
        update(session, CompanyBrowserTaskStatus.WAITING_MANUAL, message);
    }

    private void goToSearchPage(Session session) {
        Response response = session.page.navigate(GSXT_URL,
                new Page.NavigateOptions().setTimeout(properties.getTimeoutMillis()));
        setCurrentUrl(session, session.page.url());
        if (response != null && response.status() == 521) {
            throw new IllegalStateException("国家企业信用信息公示系统返回 HTTP 521");
        }
        session.page.waitForTimeout(700);
    }

    private CompanyBrowserTaskRequest currentParserRequest(Session session) {
        CompanyBrowserTaskRequest request = new CompanyBrowserTaskRequest();
        request.setCompanyName(session.task.getCompanyName());
        request.setCreditCode(session.task.getCreditCode());
        request.setJobId(session.task.getJobId());
        return request;
    }

    private boolean isBatch(Session session) {
        return StringUtils.hasText(session.task.getJobId());
    }

    private boolean isCompanyDetailPage(String text) {
        return StringUtils.hasText(text) && (text.contains("企业基本信息") || text.contains("登记信息")
                || text.contains("营业执照信息")) && (text.contains("法定代表人") || text.contains("统一社会信用代码"));
    }

    private boolean containsNoResult(String body) {
        return body != null && (body.contains("未查询到") || body.contains("暂无数据")
                || body.contains("无查询结果") || body.contains("没有找到"));
    }

    private Locator firstVisible(Page page, List<String> selectors) {
        for (String selector : selectors) {
            Locator locator = page.locator(selector);
            if (locator.count() > 0 && locator.first().isVisible()) {
                return locator.first();
            }
        }
        return null;
    }

    private Locator findCandidateLink(Page page, String companyName) {
        if (!StringUtils.hasText(companyName)) {
            return null;
        }
        String expected = CompanyNameNormalizer.normalize(companyName);
        Locator controls = page.locator("a, [role='link'], button");
        int count = Math.min(controls.count(), 300);
        for (int i = 0; i < count; i++) {
            Locator candidate = controls.nth(i);
            if (!candidate.isVisible()) {
                continue;
            }
            String text = safeText(candidate);
            if (expected.equals(CompanyNameNormalizer.normalize(text))) {
                return candidate;
            }
        }
        return null;
    }

    private Locator findClickableByText(Page page, List<String> labels) {
        Locator controls = page.locator("a, button, [role='tab']");
        int count = Math.min(controls.count(), 300);
        for (int i = 0; i < count; i++) {
            Locator control = controls.nth(i);
            if (!control.isVisible()) {
                continue;
            }
            String text = safeText(control);
            for (String label : labels) {
                if (label.equals(text) || (text.length() <= 40 && text.contains(label))) {
                    return control;
                }
            }
        }
        return null;
    }

    private Locator nextPage(Page page) {
        return firstVisible(page, List.of(
                "[class*='pagination' i] button:has-text('下一页')",
                "[class*='pagination' i] a:has-text('下一页')",
                "button:has-text('下一页')", "a:has-text('下一页')",
                ".ant-pagination-next a", ".ant-pagination-next"));
    }

    private boolean isDisabled(Locator locator) {
        String disabled = locator.getAttribute("disabled");
        String ariaDisabled = locator.getAttribute("aria-disabled");
        String classes = locator.getAttribute("class");
        return disabled != null || "true".equalsIgnoreCase(ariaDisabled)
                || (classes != null && classes.toLowerCase(Locale.ROOT).contains("disabled"));
    }

    private boolean hasManualChallenge(Page page) {
        String text = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        return text.contains("验证码") || text.contains("滑块") || text.contains("安全验证")
                || text.contains("captcha") || text.contains("访问频繁");
    }

    private boolean isAccessBlocked(String text) {
        return text != null && (text.contains("当前页面禁止访问") || text.contains("页面禁止访问")
                || text.contains("access denied") || text.contains("request blocked"));
    }

    private String pageText(Session session) {
        String text = session.page.locator("body").innerText();
        setCurrentUrl(session, session.page.url());
        return text == null ? "" : text;
    }

    private String joinSectionText(CompanyInfoSection section, List<String> pages) {
        StringBuilder text = new StringBuilder(sectionLabel(section));
        for (int index = 0; index < pages.size(); index++) {
            text.append("\n--- 第 ").append(index + 1).append(" 页 ---\n").append(pages.get(index));
        }
        return text.toString();
    }

    private String sectionLabel(CompanyInfoSection section) {
        return SECTION_LABELS.get(section).get(0);
    }

    private String signature(String text) {
        return Integer.toHexString((text == null ? "" : text.replaceAll("\\s+", "").trim()).hashCode());
    }

    private String safeText(Locator locator) {
        try {
            return locator.innerText().trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private void requireOpenPage(Session session) {
        if (session.page == null || session.page.isClosed()) {
            throw new IllegalStateException("浏览器页面不可用，请重新启动自动采集");
        }
    }

    private void saveScreenshot(Session session, String stage) {
        try {
            Files.createDirectories(properties.getScreenshotDir());
            Path path = properties.getScreenshotDir().resolve(session.taskId + "-" + stage + ".png");
            session.page.screenshot(new Page.ScreenshotOptions().setPath(path));
            synchronized (session) {
                session.task.setScreenshotPath(path.toAbsolutePath().normalize().toString());
                touchLocked(session);
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.debug("Unable to save browser screenshot for task {}", session.taskId, ex);
        }
    }

    private void closeIfTerminal(Session session) {
        CompanyBrowserTaskStatus status;
        synchronized (session) {
            status = session.task.getStatus();
        }
        if (status == CompanyBrowserTaskStatus.FOUND || status == CompanyBrowserTaskStatus.NOT_FOUND
                || status == CompanyBrowserTaskStatus.FAILED || status == CompanyBrowserTaskStatus.CLOSED
                || status == CompanyBrowserTaskStatus.COMPLETED) {
            closeSession(session);
        }
    }

    private boolean isActive(Session session) {
        synchronized (session) {
            CompanyBrowserTaskStatus status = session.task.getStatus();
            return status == CompanyBrowserTaskStatus.CREATED || status == CompanyBrowserTaskStatus.RUNNING
                    || status == CompanyBrowserTaskStatus.WAITING_MANUAL
                    || status == CompanyBrowserTaskStatus.WAITING_SELECTION
                    || status == CompanyBrowserTaskStatus.COLLECTING;
        }
    }

    private void expire(String taskId) {
        Session session = sessions.remove(taskId);
        if (session == null) {
            return;
        }
        update(session, CompanyBrowserTaskStatus.CLOSED, "浏览器查询任务已超时并停止");
        browserExecutor.execute(() -> closeSession(session));
    }

    private void closeSession(Session session) {
        try {
            if (session.context != null) {
                session.context.close();
            }
        } catch (RuntimeException ex) {
            log.debug("Unable to close browser context for task {}", session.taskId, ex);
        } finally {
            session.context = null;
            session.page = null;
        }
        try {
            if (session.playwright != null) {
                session.playwright.close();
            }
        } catch (RuntimeException ex) {
            log.debug("Unable to close Playwright for task {}", session.taskId, ex);
        } finally {
            session.playwright = null;
        }
    }

    private void ensurePlaywrightDriverResource() {
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        if (current != null && current.getResource("driver/win32_x64") != null) {
            return;
        }
        synchronized (driverLock) {
            if (driverClassLoader != null) {
                Thread.currentThread().setContextClassLoader(driverClassLoader);
                return;
            }
            try {
                Path runtimeDir = properties.getUserDataDir().resolve(".runtime");
                Path bundle = runtimeDir.resolve("driver-bundle.jar");
                Files.createDirectories(runtimeDir);
                if (!Files.exists(bundle) || Files.size(bundle) == 0) {
                    extractDriverBundle(bundle);
                }
                URLClassLoader loader = new URLClassLoader(new URL[]{bundle.toUri().toURL()}, current);
                if (loader.getResource("driver/win32_x64") == null) {
                    loader.close();
                    throw new IllegalStateException("Playwright driver-bundle 中缺少 Windows 驱动资源");
                }
                driverClassLoader = loader;
                Thread.currentThread().setContextClassLoader(loader);
            } catch (Exception ex) {
                throw new IllegalStateException("无法加载 Playwright 浏览器驱动", ex);
            }
        }
    }

    private void extractDriverBundle(Path target) throws Exception {
        List<Path> candidates = new ArrayList<>();
        URL location = DefaultCompanyBrowserLookupService.class.getProtectionDomain().getCodeSource().getLocation();
        if (location != null && "file".equalsIgnoreCase(location.getProtocol())) {
            candidates.add(Path.of(location.toURI()));
        }
        candidates.add(Path.of(System.getProperty("user.dir"), "target", "company-profile-query-0.1.0-SNAPSHOT.jar"));
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (JarFile jar = new JarFile(candidate.toFile())) {
                JarEntry bundleEntry = jar.stream().filter(entry -> entry.getName().startsWith("BOOT-INF/lib/driver-bundle-"))
                        .filter(entry -> entry.getName().endsWith(".jar")).findFirst().orElse(null);
                if (bundleEntry == null) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(bundleEntry)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }
        }
        throw new IllegalStateException("未在可执行 JAR 中找到 Playwright driver-bundle");
    }

    private Session requireSession(String taskId) {
        Session session = sessions.get(taskId);
        if (session == null) {
            throw new IllegalArgumentException("浏览器查询任务不存在：" + taskId);
        }
        return session;
    }

    private CompanyBrowserTask snapshot(Session session) {
        synchronized (session) {
            return CompanyBrowserTask.builder().taskId(session.task.getTaskId())
                    .companyName(session.task.getCompanyName()).creditCode(session.task.getCreditCode())
                    .jobId(session.task.getJobId()).currentCompanyId(session.task.getCurrentCompanyId())
                    .status(session.task.getStatus()).message(session.task.getMessage())
                    .currentUrl(session.task.getCurrentUrl()).screenshotPath(session.task.getScreenshotPath())
                    .candidates(session.task.getCandidates() == null ? List.of() : List.copyOf(session.task.getCandidates()))
                    .sectionStatuses(session.task.getSectionStatuses() == null ? Map.of() : Map.copyOf(session.task.getSectionStatuses()))
                    .totalCompanies(session.task.getTotalCompanies()).completedCompanies(session.task.getCompletedCompanies())
                    .createdAt(session.task.getCreatedAt()).updatedAt(session.task.getUpdatedAt()).build();
        }
    }

    private void update(Session session, CompanyBrowserTaskStatus status, String message) {
        synchronized (session) {
            session.task.setStatus(status);
            session.task.setMessage(message);
            touchLocked(session);
        }
    }

    private void fail(Session session, String message) {
        update(session, CompanyBrowserTaskStatus.FAILED,
                StringUtils.hasText(message) ? message : "浏览器查询失败");
    }

    private void setCurrentUrl(Session session, String currentUrl) {
        synchronized (session) {
            session.task.setCurrentUrl(currentUrl);
            touchLocked(session);
        }
    }

    private void touchLocked(Session session) {
        session.task.setUpdatedAt(LocalDateTime.now());
    }

    private String readableMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @PreDestroy
    public void shutdown() {
        try {
            browserExecutor.submit(() -> sessions.values().forEach(this::closeSession)).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Unable to close all browser sessions during shutdown", ex);
        }
        browserExecutor.shutdownNow();
        cleanupExecutor.shutdownNow();
        if (driverClassLoader instanceof URLClassLoader loader) {
            try {
                loader.close();
            } catch (Exception ex) {
                log.debug("Unable to close driver classloader", ex);
            }
        }
    }

    private static Map<CompanyInfoSection, List<String>> sectionLabels() {
        Map<CompanyInfoSection, List<String>> labels = new EnumMap<>(CompanyInfoSection.class);
        labels.put(CompanyInfoSection.BASIC, List.of("企业基本信息", "登记信息", "营业执照信息"));
        labels.put(CompanyInfoSection.SHAREHOLDERS, List.of("股东及出资信息", "股东及出资", "股东信息"));
        labels.put(CompanyInfoSection.INVESTMENTS, List.of("对外投资信息", "对外投资"));
        labels.put(CompanyInfoSection.POSITIONS, List.of("主要人员信息", "主要人员", "任职信息"));
        labels.put(CompanyInfoSection.ABNORMAL, List.of("经营异常名录信息", "经营异常信息", "经营异常"));
        labels.put(CompanyInfoSection.SERIOUS_VIOLATIONS,
                List.of("严重违法失信信息", "严重违法失信名单", "严重违法失信"));
        return Map.copyOf(labels);
    }

    private record SectionCapture(String text, boolean requiresVerification) {
    }

    private static final class Session {
        private final String taskId;
        private final CompanyBrowserTask task;
        private final Map<CompanyInfoSection, String> sectionStatuses = new EnumMap<>(CompanyInfoSection.class);
        private CompanyBatchAutomationTarget currentTarget;
        private CompanyProfile selectedCandidate;
        private String basicText;
        private int nextSectionIndex;
        private boolean waitingForVerification;
        private Playwright playwright;
        private BrowserContext context;
        private Page page;

        private Session(String taskId, CompanyBrowserTaskRequest request) {
            this.taskId = taskId;
            LocalDateTime now = LocalDateTime.now();
            this.task = CompanyBrowserTask.builder().taskId(taskId).companyName(request.getCompanyName())
                    .creditCode(request.getCreditCode()).jobId(request.getJobId()).status(CompanyBrowserTaskStatus.CREATED)
                    .message("浏览器查询任务已创建").candidates(List.of()).sectionStatuses(Map.of())
                    .createdAt(now).updatedAt(now).build();
        }
    }
}
