package com.guquan.equity.service;

import com.guquan.equity.api.CompanyBrowserLookupService;
import com.guquan.equity.model.CompanyBrowserTask;
import com.guquan.equity.model.CompanyBrowserTaskRequest;
import com.guquan.equity.model.CompanyBrowserTaskStatus;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.provider.CompanyBrowserProperties;
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
import java.util.List;
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

@Service
public class DefaultCompanyBrowserLookupService implements CompanyBrowserLookupService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCompanyBrowserLookupService.class);
    private static final String GSXT_URL = "https://www.gsxt.gov.cn/";
    private static final List<String> SEARCH_INPUTS = List.of(
            "input[placeholder*='企业']", "input[placeholder*='统一社会信用代码']",
            "input[placeholder*='关键词']", "input[type='search']",
            "input[name*='keyword' i]", "input[id*='keyword' i]", "input[id*='search' i]");
    private static final List<String> SEARCH_BUTTONS = List.of(
            "button:has-text('查询')", "button:has-text('搜索')",
            "input[type='submit']", "[class*='search' i] button");

    private final CompanyBrowserProperties properties;
    private final GsxtPageParser pageParser;
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

    public DefaultCompanyBrowserLookupService(CompanyBrowserProperties properties, GsxtPageParser pageParser) {
        this.properties = properties;
        this.pageParser = pageParser;
    }

    @Override
    public synchronized CompanyBrowserTask start(CompanyBrowserTaskRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("浏览器辅助查询功能已关闭");
        }
        if (request == null || (!StringUtils.hasText(request.getCompanyName())
                && !StringUtils.hasText(request.getCreditCode()))) {
            throw new IllegalArgumentException("companyName 或 creditCode 至少填写一项");
        }
        if (sessions.values().stream().anyMatch(this::isActive)) {
            throw new IllegalStateException("已有浏览器查询正在运行，请先完成或关闭当前任务");
        }
        CompanyBrowserTaskRequest safeRequest = new CompanyBrowserTaskRequest();
        safeRequest.setCompanyName(trimToNull(request.getCompanyName()));
        safeRequest.setCreditCode(trimToNull(request.getCreditCode()));
        String taskId = UUID.randomUUID().toString().replace("-", "");
        Session session = new Session(taskId, safeRequest);
        sessions.put(taskId, session);
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
                if (session.page == null || session.page.isClosed()) {
                    throw new IllegalStateException("浏览器页面不可用");
                }
                extractResults(session);
                closeIfTerminal(session);
                result.complete(snapshot(session));
            } catch (RuntimeException ex) {
                log.error("GSXT browser lookup failed for task {}", session.taskId, ex);
                fail(session, readableMessage(ex));
                closeSession(session);
                result.complete(snapshot(session));
            }
        });
        try {
            return result.get(Math.max(10, properties.getTimeoutMillis() / 1000 + 5), TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("继续解析浏览器页面超时", ex);
        }
    }

    @Override
    public void close(String taskId) {
        Session session = sessions.remove(taskId);
        if (session == null) {
            return;
        }
        update(session, CompanyBrowserTaskStatus.CLOSED, "浏览器查询任务已关闭");
        browserExecutor.execute(() -> closeSession(session));
    }

    private void openAndSearch(Session session) {
        update(session, CompanyBrowserTaskStatus.RUNNING, "正在打开国家企业信用信息公示系统");
        try {
            ensurePlaywrightDriverResource();
            session.playwright = Playwright.create();
            Path profileDir = properties.getUserDataDir().resolve("shared");
            Files.createDirectories(profileDir);
            session.context = session.playwright.chromium().launchPersistentContext(
                    profileDir,
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(properties.isHeadless())
                            .setLocale("zh-CN")
                            .setViewportSize(1365, 900));
            session.page = session.context.pages().isEmpty()
                    ? session.context.newPage() : session.context.pages().get(0);
            session.page.setDefaultTimeout(properties.getTimeoutMillis());
            Response response = session.page.navigate(GSXT_URL, new Page.NavigateOptions()
                    .setTimeout(properties.getTimeoutMillis()));
            setCurrentUrl(session, session.page.url());
            saveScreenshot(session, "opened");

            String openedText = session.page.locator("body").innerText().toLowerCase();
            if ((response != null && response.status() == 521)
                    || openedText.contains("web server is down") || openedText.contains("error 521")) {
                fail(session, "国家企业信用信息公示系统首页返回 HTTP 521，官方站点暂时不可达，请稍后重试");
                closeSession(session);
                return;
            }
            if (isAccessBlocked(openedText)) {
                fail(session, "国家企业信用信息公示系统拒绝当前网络或浏览器会话访问，请稍后重试或使用普通浏览器复制页面内容导入");
                closeSession(session);
                return;
            }

            Locator input = firstVisible(session.page, SEARCH_INPUTS);
            if (input == null) {
                update(session, CompanyBrowserTaskStatus.WAITING_MANUAL,
                        "官方网页已打开但尚未显示搜索框，请等待页面加载或手工操作后点击继续解析");
                return;
            }
            String keyword = StringUtils.hasText(session.request.getCompanyName())
                    ? session.request.getCompanyName() : session.request.getCreditCode();
            input.fill(keyword);
            Locator button = firstVisible(session.page, SEARCH_BUTTONS);
            if (button != null) {
                button.click();
            } else {
                input.press("Enter");
            }
            session.page.waitForTimeout(2500);
            setCurrentUrl(session, session.page.url());
            saveScreenshot(session, "searched");

            if (hasManualChallenge(session.page)) {
                update(session, CompanyBrowserTaskStatus.WAITING_MANUAL,
                        "请在浏览器中完成验证码或人工验证，然后点击继续解析");
            } else {
                extractResults(session);
                closeIfTerminal(session);
            }
        } catch (Exception ex) {
            log.error("GSXT browser startup failed for task {}", session.taskId, ex);
            fail(session, readableMessage(ex));
            closeSession(session);
        }
    }

    private void extractResults(Session session) {
        String body = session.page.locator("body").innerText();
        List<CompanyProfile> profiles = pageParser.parse(body, session.request);
        synchronized (session) {
            session.task.setCandidates(List.copyOf(profiles));
            session.task.setCurrentUrl(session.page.url());
            if (!profiles.isEmpty()) {
                session.task.setStatus(CompanyBrowserTaskStatus.FOUND);
                session.task.setMessage("已解析出候选单位，可保存到本地档案");
            } else if (containsNoResult(body)) {
                session.task.setStatus(CompanyBrowserTaskStatus.NOT_FOUND);
                session.task.setMessage("官方页面明确显示没有匹配结果");
            } else {
                session.task.setStatus(CompanyBrowserTaskStatus.WAITING_MANUAL);
                session.task.setMessage("当前页面尚未解析到有效信用代码，请确认验证码和结果页后再次继续解析");
            }
            touchLocked(session);
        }
        saveScreenshot(session, "parsed");
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

    private boolean hasManualChallenge(Page page) {
        String text = page.locator("body").innerText().toLowerCase();
        return text.contains("验证码") || text.contains("滑块") || text.contains("安全验证")
                || text.contains("captcha") || text.contains("访问频繁");
    }

    private boolean isAccessBlocked(String text) {
        return text != null && (text.contains("当前页面禁止访问")
                || text.contains("页面禁止访问")
                || text.contains("access denied")
                || text.contains("request blocked"));
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
                || status == CompanyBrowserTaskStatus.FAILED || status == CompanyBrowserTaskStatus.CLOSED) {
            closeSession(session);
        }
    }

    private boolean isActive(Session session) {
        synchronized (session) {
            CompanyBrowserTaskStatus status = session.task.getStatus();
            return status == CompanyBrowserTaskStatus.CREATED
                    || status == CompanyBrowserTaskStatus.RUNNING
                    || status == CompanyBrowserTaskStatus.WAITING_MANUAL;
        }
    }

    private void expire(String taskId) {
        Session session = sessions.remove(taskId);
        if (session == null) {
            return;
        }
        update(session, CompanyBrowserTaskStatus.CLOSED, "浏览器查询任务已超时并自动关闭");
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
        URL location = DefaultCompanyBrowserLookupService.class
                .getProtectionDomain().getCodeSource().getLocation();
        if (location != null && "file".equalsIgnoreCase(location.getProtocol())) {
            candidates.add(Path.of(location.toURI()));
        }
        candidates.add(Path.of(System.getProperty("user.dir"), "target",
                "company-profile-query-0.1.0-SNAPSHOT.jar"));
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (JarFile jar = new JarFile(candidate.toFile())) {
                JarEntry bundleEntry = jar.stream()
                        .filter(entry -> entry.getName().startsWith("BOOT-INF/lib/driver-bundle-"))
                        .filter(entry -> entry.getName().endsWith(".jar"))
                        .findFirst().orElse(null);
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
            return CompanyBrowserTask.builder()
                    .taskId(session.task.getTaskId())
                    .companyName(session.task.getCompanyName())
                    .creditCode(session.task.getCreditCode())
                    .status(session.task.getStatus())
                    .message(session.task.getMessage())
                    .currentUrl(session.task.getCurrentUrl())
                    .screenshotPath(session.task.getScreenshotPath())
                    .candidates(session.task.getCandidates() == null
                            ? List.of() : List.copyOf(session.task.getCandidates()))
                    .createdAt(session.task.getCreatedAt())
                    .updatedAt(session.task.getUpdatedAt())
                    .build();
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
            browserExecutor.submit(() -> sessions.values().forEach(this::closeSession))
                    .get(10, TimeUnit.SECONDS);
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

    private static final class Session {
        private final String taskId;
        private final CompanyBrowserTaskRequest request;
        private final CompanyBrowserTask task;
        private Playwright playwright;
        private BrowserContext context;
        private Page page;

        private Session(String taskId, CompanyBrowserTaskRequest request) {
            this.taskId = taskId;
            this.request = request;
            LocalDateTime now = LocalDateTime.now();
            this.task = CompanyBrowserTask.builder()
                    .taskId(taskId)
                    .companyName(request.getCompanyName())
                    .creditCode(request.getCreditCode())
                    .status(CompanyBrowserTaskStatus.CREATED)
                    .message("浏览器查询任务已创建")
                    .candidates(List.of())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }
    }
}
