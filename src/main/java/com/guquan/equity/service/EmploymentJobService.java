package com.guquan.equity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guquan.equity.api.CompanyProfileCacheService;
import com.guquan.equity.model.CompanyBrowserTaskRequest;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.EmploymentAreaOption;
import com.guquan.equity.model.EmploymentCandidateView;
import com.guquan.equity.model.EmploymentCompanyStatus;
import com.guquan.equity.model.EmploymentCompanyTaskView;
import com.guquan.equity.model.EmploymentConfirmRequest;
import com.guquan.equity.model.EmploymentImportPreview;
import com.guquan.equity.model.EmploymentJobStatus;
import com.guquan.equity.model.EmploymentJobSummary;
import com.guquan.equity.provider.EmploymentJobProperties;
import com.guquan.equity.repository.EmploymentCompanyTaskEntity;
import com.guquan.equity.repository.EmploymentCompanyTaskRepository;
import com.guquan.equity.repository.EmploymentJobEntity;
import com.guquan.equity.repository.EmploymentJobRepository;
import com.guquan.equity.repository.EmploymentRowEntity;
import com.guquan.equity.repository.EmploymentRowRepository;
import com.guquan.equity.util.UnifiedSocialCreditCodeUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EmploymentJobService {

    private static final int MAX_PAGE_TEXT = 2_000_000;
    private static final Set<String> DECISIONS = Set.of("KEEP_EXISTING", "USE_QUERY");

    private final EmploymentJobProperties properties;
    private final EmploymentWorkbookService workbookService;
    private final EmploymentStandardizationService standardizationService;
    private final CompanyProfileCacheService cacheService;
    private final GsxtPageParser pageParser;
    private final EmploymentJobRepository jobRepository;
    private final EmploymentCompanyTaskRepository companyRepository;
    private final EmploymentRowRepository rowRepository;
    private final ObjectMapper objectMapper;

    public EmploymentJobService(
            EmploymentJobProperties properties,
            EmploymentWorkbookService workbookService,
            EmploymentStandardizationService standardizationService,
            CompanyProfileCacheService cacheService,
            GsxtPageParser pageParser,
            EmploymentJobRepository jobRepository,
            EmploymentCompanyTaskRepository companyRepository,
            EmploymentRowRepository rowRepository,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.workbookService = workbookService;
        this.standardizationService = standardizationService;
        this.cacheService = cacheService;
        this.pageParser = pageParser;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.rowRepository = rowRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EmploymentJobSummary create(MultipartFile file) {
        validateUpload(file);
        String jobId = UUID.randomUUID().toString().replace("-", "");
        Path jobDirectory = safeJobDirectory(jobId);
        Path inputPath = jobDirectory.resolve("input.xlsx");
        try {
            Files.createDirectories(jobDirectory);
            try (var input = file.getInputStream()) {
                Files.copy(input, inputPath);
            }
            EmploymentWorkbookService.ParsedWorkbook parsed = workbookService.parse(inputPath);
            LocalDateTime now = LocalDateTime.now();
            EmploymentJobEntity job = new EmploymentJobEntity();
            job.setId(jobId);
            job.setOriginalFilename(safeFilename(file.getOriginalFilename()));
            job.setInputPath(inputPath.toAbsolutePath().normalize().toString());
            job.setStatus(EmploymentJobStatus.IN_PROGRESS.name());
            job.setTotalRecords(parsed.records().size());
            Map<String, List<EmploymentWorkbookService.EmploymentRecord>> groups = parsed.records().stream()
                    .collect(Collectors.groupingBy(
                            EmploymentWorkbookService.EmploymentRecord::normalizedCompanyName,
                            LinkedHashMap::new,
                            Collectors.toList()));
            job.setUniqueCompanies(groups.size());
            job.setValidationErrorsJson(writeJson(parsed.validationErrors()));
            job.setCreatedAt(now);
            job.setUpdatedAt(now);
            job.setExpiresAt(now.plusDays(Math.max(1, properties.getRetentionDays())));
            jobRepository.save(job);

            for (List<EmploymentWorkbookService.EmploymentRecord> records : groups.values()) {
                EmploymentWorkbookService.EmploymentRecord first = records.get(0);
                EmploymentCompanyTaskEntity task = new EmploymentCompanyTaskEntity();
                task.setJobId(jobId);
                task.setInputCompanyName(first.companyName());
                task.setNormalizedName(first.normalizedCompanyName());
                task.setStatus(EmploymentCompanyStatus.NEEDS_COLLECTION.name());
                task.setAffectedRows(records.size());
                task.setCreatedAt(now);
                task.setUpdatedAt(now);
                task = companyRepository.save(task);

                List<EmploymentRowEntity> rows = new ArrayList<>();
                for (EmploymentWorkbookService.EmploymentRecord record : records) {
                    EmploymentRowEntity row = new EmploymentRowEntity();
                    row.setJobId(jobId);
                    row.setCompanyTaskId(task.getId());
                    row.setRowNumber(record.rowNumber());
                    row.setCreatedAt(now);
                    rows.add(row);
                }
                rowRepository.saveAll(rows);

                var cached = cacheService.findExact(first.companyName());
                if (cached.isPresent()) {
                    applyProfile(task, cached.get(), parsed.references(), true, false, Map.of());
                    companyRepository.save(task);
                }
            }
            updateJobStatus(job);
            return summary(job, parsed.references());
        } catch (RuntimeException | IOException ex) {
            deleteDirectory(jobDirectory);
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("保存上传文件失败", ex);
        }
    }

    public EmploymentJobSummary get(String jobId) {
        EmploymentJobEntity job = requireJob(jobId);
        return summary(job, workbookService.readReferences(Path.of(job.getInputPath())));
    }

    public List<EmploymentCompanyTaskView> companies(String jobId) {
        requireJob(jobId);
        return companyRepository.findByJobIdOrderByInputCompanyNameAsc(jobId).stream()
                .map(this::toView)
                .toList();
    }

    public List<EmploymentAreaOption> searchAreas(String jobId, String query) {
        EmploymentJobEntity job = requireJob(jobId);
        return workbookService.searchAreas(Path.of(job.getInputPath()), query);
    }

    public EmploymentImportPreview importPage(String jobId, Long companyId, String pageText) {
        EmploymentCompanyTaskEntity task = requireTask(jobId, companyId);
        if (!StringUtils.hasText(pageText)) {
            throw new IllegalArgumentException("请粘贴国家企业信用信息公示系统页面文字");
        }
        if (pageText.length() > MAX_PAGE_TEXT) {
            throw new IllegalArgumentException("粘贴内容过大，请仅复制企业结果或详情区域");
        }
        EmploymentJobEntity job = requireJob(jobId);
        EmploymentWorkbookService.ReferenceData references =
                workbookService.readReferences(Path.of(job.getInputPath()));
        CompanyBrowserTaskRequest parserRequest = new CompanyBrowserTaskRequest();
        parserRequest.setCompanyName(task.getInputCompanyName());
        List<EmploymentCandidateView> candidates = pageParser
                .parse(pageText, parserRequest, "GSXT_MANUAL_IMPORT")
                .stream()
                .map(profile -> candidate(profile, references))
                .toList();
        return EmploymentImportPreview.builder()
                .candidateCount(candidates.size())
                .candidates(candidates)
                .message(candidates.isEmpty()
                        ? "未识别到有效企业，请确认复制了结果页或详情页文字"
                        : "解析完成，请核对企业和标准分类后确认")
                .build();
    }

    /** Saves the fields copied from the public website and defers all template-field completion. */
    @Transactional
    public EmploymentCompanyTaskView collect(
            String jobId,
            Long companyId,
            EmploymentConfirmRequest request) {
        EmploymentCompanyTaskEntity task = requireTask(jobId, companyId);
        EmploymentJobEntity job = requireJob(jobId);
        if (request == null || request.getProfile() == null
                || !StringUtils.hasText(request.getProfile().getCompanyName())) {
            throw new IllegalArgumentException("请选择要记录的企业档案");
        }
        CompanyProfile profile = request.getProfile();
        if (!StringUtils.hasText(profile.getSource())) {
            profile.setSource("GSXT_MANUAL_IMPORT");
        }
        CompanyProfile saved = cacheService.save(profile);
        cacheService.saveAlias(task.getInputCompanyName(), saved);

        EmploymentWorkbookService.ReferenceData references =
                workbookService.readReferences(Path.of(job.getInputPath()));
        EmploymentStandardizationService.Suggestion suggestion =
                standardizationService.suggest(saved, references);
        task.setOfficialCompanyName(saved.getCompanyName());
        task.setCreditCode(trimToNull(saved.getCreditCode()));
        task.setRawIndustryName(trimToNull(saved.getIndustryName()));
        task.setRawEntityType(trimToNull(saved.getEntityType()));
        task.setRegisteredAddress(trimToNull(saved.getRegisteredAddress()));
        task.setEmploymentIndustry(firstTemplateValue(
                request.getEmploymentIndustry(), suggestion.industry(), references.industries()));
        task.setEmploymentUnitNature(firstTemplateValue(
                request.getEmploymentUnitNature(), suggestion.unitNature(), references.unitNatures()));
        String areaCode = StringUtils.hasText(request.getRegisteredAddressAreaCode())
                ? request.getRegisteredAddressAreaCode().trim() : suggestion.areaCode();
        task.setRegisteredAddressAreaCode(areaCode);
        task.setRegisteredAddressAreaName(references.areaNames().get(areaCode));
        task.setClassificationSource("COLLECTED_PENDING_REVIEW");
        task.setClassificationConfidence("PENDING");
        task.setSource(trimToNull(saved.getSource()));
        task.setCacheHit(false);
        task.setConflictFieldsJson("[]");
        task.setConflictDecisionsJson("{}");
        task.setStatus(EmploymentCompanyStatus.NEEDS_REVIEW.name());
        task.setUpdatedAt(LocalDateTime.now());
        companyRepository.save(task);
        updateJobStatus(job);
        return toView(task);
    }

    @Transactional
    public EmploymentCompanyTaskView confirm(
            String jobId,
            Long companyId,
            EmploymentConfirmRequest request) {
        EmploymentCompanyTaskEntity task = requireTask(jobId, companyId);
        EmploymentJobEntity job = requireJob(jobId);
        if (request == null || request.getProfile() == null) {
            throw new IllegalArgumentException("请选择或填写要确认的企业档案");
        }
        CompanyProfile profile = request.getProfile();
        if (!StringUtils.hasText(profile.getCompanyName())) {
            throw new IllegalArgumentException("企业名称不能为空");
        }
        EmploymentWorkbookService.ReferenceData references =
                workbookService.readReferences(Path.of(job.getInputPath()));
        validateConfirmedValues(request, references);
        validateDecisions(request.getConflictDecisions());

        String areaCode = request.getRegisteredAddressAreaCode().trim();
        profile.setEmploymentIndustry(request.getEmploymentIndustry().trim());
        profile.setEmploymentUnitNature(request.getEmploymentUnitNature().trim());
        profile.setClassificationSource("MANUAL_REVIEW");
        profile.setClassificationConfidence("HIGH");
        profile.setRegisteredAddressAreaCode(areaCode);
        profile.setAreaCode(areaCode);
        profile.setAreaCodeSource("MANUAL_OR_ADDRESS");
        if (!StringUtils.hasText(profile.getSource())) {
            profile.setSource("GSXT_MANUAL_IMPORT");
        }
        CompanyProfile saved = cacheService.save(profile);
        cacheService.saveAlias(task.getInputCompanyName(), saved);
        applyProfile(
                task,
                saved,
                references,
                false,
                Boolean.TRUE.equals(request.getReplaceOfficialName()),
                request.getConflictDecisions() == null ? Map.of() : request.getConflictDecisions());
        companyRepository.save(task);
        updateJobStatus(job);
        return toView(task);
    }

    @Transactional
    public ExportedFile export(String jobId, String mode) {
        EmploymentJobEntity job = requireJob(jobId);
        boolean finalMode = !"draft".equalsIgnoreCase(mode);
        List<EmploymentCompanyTaskEntity> tasks =
                companyRepository.findByJobIdOrderByInputCompanyNameAsc(jobId);
        if (finalMode) {
            List<String> jobErrors = readStringList(job.getValidationErrorsJson());
            if (!jobErrors.isEmpty()) {
                throw new IllegalStateException("模板存在人员校验错误，请先修正：" + jobErrors.get(0));
            }
            List<String> unfinished = tasks.stream()
                    .filter(task -> !EmploymentCompanyStatus.RESOLVED.name().equals(task.getStatus()))
                    .map(EmploymentCompanyTaskEntity::getInputCompanyName)
                    .limit(5)
                    .toList();
            if (!unfinished.isEmpty()) {
                throw new IllegalStateException("仍有单位未完成采集或审核：" + String.join("、", unfinished));
            }
        }

        List<EmploymentWorkbookService.ExportGroup> groups = tasks.stream()
                .filter(this::hasExportableValues)
                .map(this::exportGroup)
                .toList();
        Path directory = safeJobDirectory(jobId);
        String suffix = finalMode ? "企业信息已补全" : "企业信息检查版";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String baseName = removeXlsxSuffix(job.getOriginalFilename());
        String filename = baseName + "_" + suffix + "_" + timestamp + ".xlsx";
        Path outputPath = directory.resolve(filename).normalize();
        ensureWithinRoot(outputPath);
        workbookService.export(Path.of(job.getInputPath()), outputPath, groups);
        if (finalMode) {
            List<String> validationErrors = workbookService.validateCompanyFields(outputPath);
            if (!validationErrors.isEmpty()) {
                tryDelete(outputPath);
                throw new IllegalStateException("导出文件未通过最终校验：" + validationErrors.get(0));
            }
            job.setStatus(EmploymentJobStatus.EXPORTED.name());
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
        return new ExportedFile(outputPath, filename);
    }

    @Transactional
    public void delete(String jobId) {
        EmploymentJobEntity job = requireJob(jobId);
        rowRepository.deleteByJobId(jobId);
        companyRepository.deleteByJobId(jobId);
        jobRepository.delete(job);
        deleteDirectory(safeJobDirectory(jobId));
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpired() {
        List<EmploymentJobEntity> expired = jobRepository.findByExpiresAtBefore(LocalDateTime.now());
        for (EmploymentJobEntity job : expired) {
            rowRepository.deleteByJobId(job.getId());
            companyRepository.deleteByJobId(job.getId());
            jobRepository.delete(job);
            deleteDirectory(safeJobDirectory(job.getId()));
        }
    }

    private EmploymentCandidateView candidate(
            CompanyProfile profile,
            EmploymentWorkbookService.ReferenceData references) {
        EmploymentStandardizationService.Suggestion suggestion =
                standardizationService.suggest(profile, references);
        return EmploymentCandidateView.builder()
                .profile(profile)
                .employmentIndustry(suggestion.industry())
                .employmentUnitNature(suggestion.unitNature())
                .registeredAddressAreaCode(suggestion.areaCode())
                .registeredAddressAreaName(suggestion.areaName())
                .classificationConfidence(suggestion.confidence())
                .build();
    }

    private void applyProfile(
            EmploymentCompanyTaskEntity task,
            CompanyProfile profile,
            EmploymentWorkbookService.ReferenceData references,
            boolean cacheHit,
            boolean replaceOfficialName,
            Map<String, String> decisions) {
        EmploymentStandardizationService.Suggestion suggestion =
                standardizationService.suggest(profile, references);
        task.setOfficialCompanyName(profile.getCompanyName());
        task.setCreditCode(trimToNull(profile.getCreditCode()));
        task.setRawIndustryName(trimToNull(profile.getIndustryName()));
        task.setRawEntityType(trimToNull(profile.getEntityType()));
        task.setRegisteredAddress(trimToNull(profile.getRegisteredAddress()));
        task.setEmploymentIndustry(suggestion.industry());
        task.setEmploymentUnitNature(suggestion.unitNature());
        task.setRegisteredAddressAreaCode(suggestion.areaCode());
        task.setRegisteredAddressAreaName(suggestion.areaName());
        task.setClassificationSource(suggestion.source());
        task.setClassificationConfidence(suggestion.confidence());
        task.setSource(trimToNull(profile.getSource()));
        task.setCacheHit(cacheHit);
        task.setReplaceOfficialName(replaceOfficialName);
        task.setUpdatedAt(LocalDateTime.now());
        task.setConflictDecisionsJson(writeJson(decisions));

        if (!hasExportableValues(task)) {
            task.setConflictFieldsJson("[]");
            task.setStatus(EmploymentCompanyStatus.NEEDS_REVIEW.name());
            return;
        }
        List<Integer> rowNumbers = rowRepository.findByCompanyTaskIdOrderByRowNumberAsc(task.getId())
                .stream().map(EmploymentRowEntity::getRowNumber).toList();
        EmploymentJobEntity job = requireJob(task.getJobId());
        Set<String> conflicts = workbookService.findConflicts(
                Path.of(job.getInputPath()), rowNumbers, values(task));
        task.setConflictFieldsJson(writeJson(conflicts));
        boolean pendingConflict = conflicts.stream().anyMatch(field -> {
            String decision = decisions.get(field);
            return decision == null || !DECISIONS.contains(decision);
        });
        task.setStatus(pendingConflict
                ? EmploymentCompanyStatus.CONFLICT.name()
                : EmploymentCompanyStatus.RESOLVED.name());
    }

    private void validateConfirmedValues(
            EmploymentConfirmRequest request,
            EmploymentWorkbookService.ReferenceData references) {
        CompanyProfile profile = request.getProfile();
        if (!StringUtils.hasText(profile.getCreditCode())
                || !UnifiedSocialCreditCodeUtil.parse(profile.getCreditCode()).isValid()) {
            throw new IllegalArgumentException("统一社会信用代码为空或校验不通过");
        }
        if (!references.industries().contains(request.getEmploymentIndustry())) {
            throw new IllegalArgumentException("单位行业必须从模板代码表中选择");
        }
        if (!references.unitNatures().contains(request.getEmploymentUnitNature())) {
            throw new IllegalArgumentException("单位性质必须从模板代码表中选择");
        }
        if (!StringUtils.hasText(request.getRegisteredAddressAreaCode())
                || !references.areaNames().containsKey(request.getRegisteredAddressAreaCode().trim())) {
            throw new IllegalArgumentException("单位所在地代码必须是模板地区代码表中的六位代码");
        }
    }

    private void validateDecisions(Map<String, String> decisions) {
        if (decisions == null) {
            return;
        }
        decisions.forEach((field, decision) -> {
            if (!DECISIONS.contains(decision)) {
                throw new IllegalArgumentException("冲突处理值无效：" + field);
            }
        });
    }

    private EmploymentJobSummary summary(
            EmploymentJobEntity job,
            EmploymentWorkbookService.ReferenceData references) {
        List<EmploymentCompanyTaskEntity> tasks =
                companyRepository.findByJobIdOrderByInputCompanyNameAsc(job.getId());
        return EmploymentJobSummary.builder()
                .jobId(job.getId())
                .originalFilename(job.getOriginalFilename())
                .status(EmploymentJobStatus.valueOf(job.getStatus()))
                .totalRecords(job.getTotalRecords())
                .uniqueCompanies(job.getUniqueCompanies())
                .cacheHits((int) tasks.stream().filter(EmploymentCompanyTaskEntity::isCacheHit).count())
                .pendingCollection(count(tasks, EmploymentCompanyStatus.NEEDS_COLLECTION))
                .pendingReview(count(tasks, EmploymentCompanyStatus.NEEDS_REVIEW))
                .conflicts(count(tasks, EmploymentCompanyStatus.CONFLICT))
                .resolved(count(tasks, EmploymentCompanyStatus.RESOLVED))
                .validationErrors(readStringList(job.getValidationErrorsJson()))
                .industryOptions(new ArrayList<>(references.industries()))
                .unitNatureOptions(new ArrayList<>(references.unitNatures()))
                .createdAt(job.getCreatedAt())
                .expiresAt(job.getExpiresAt())
                .build();
    }

    private int count(List<EmploymentCompanyTaskEntity> tasks, EmploymentCompanyStatus status) {
        return (int) tasks.stream().filter(task -> status.name().equals(task.getStatus())).count();
    }

    private EmploymentCompanyTaskView toView(EmploymentCompanyTaskEntity task) {
        List<Integer> rowNumbers = rowRepository.findByCompanyTaskIdOrderByRowNumberAsc(task.getId())
                .stream().map(EmploymentRowEntity::getRowNumber).toList();
        return EmploymentCompanyTaskView.builder()
                .companyId(task.getId())
                .inputCompanyName(task.getInputCompanyName())
                .officialCompanyName(task.getOfficialCompanyName())
                .creditCode(task.getCreditCode())
                .rawIndustryName(task.getRawIndustryName())
                .rawEntityType(task.getRawEntityType())
                .registeredAddress(task.getRegisteredAddress())
                .employmentIndustry(task.getEmploymentIndustry())
                .employmentUnitNature(task.getEmploymentUnitNature())
                .registeredAddressAreaCode(task.getRegisteredAddressAreaCode())
                .registeredAddressAreaName(task.getRegisteredAddressAreaName())
                .classificationSource(task.getClassificationSource())
                .classificationConfidence(task.getClassificationConfidence())
                .source(task.getSource())
                .status(EmploymentCompanyStatus.valueOf(task.getStatus()))
                .affectedRows(task.getAffectedRows())
                .cacheHit(task.isCacheHit())
                .replaceOfficialName(task.isReplaceOfficialName())
                .rowNumbers(rowNumbers)
                .conflictFields(readStringList(task.getConflictFieldsJson()))
                .conflictDecisions(readStringMap(task.getConflictDecisionsJson()))
                .build();
    }

    private EmploymentWorkbookService.ExportGroup exportGroup(EmploymentCompanyTaskEntity task) {
        List<Integer> rows = rowRepository.findByCompanyTaskIdOrderByRowNumberAsc(task.getId())
                .stream().map(EmploymentRowEntity::getRowNumber).toList();
        return new EmploymentWorkbookService.ExportGroup(rows, values(task),
                readStringMap(task.getConflictDecisionsJson()));
    }

    private EmploymentWorkbookService.CompanyValues values(EmploymentCompanyTaskEntity task) {
        return new EmploymentWorkbookService.CompanyValues(
                task.getOfficialCompanyName(),
                task.getCreditCode(),
                task.getEmploymentIndustry(),
                task.getEmploymentUnitNature(),
                task.getRegisteredAddressAreaCode(),
                task.getRegisteredAddressAreaName(),
                task.isReplaceOfficialName());
    }

    private boolean hasExportableValues(EmploymentCompanyTaskEntity task) {
        return StringUtils.hasText(task.getOfficialCompanyName())
                && StringUtils.hasText(task.getCreditCode())
                && StringUtils.hasText(task.getEmploymentIndustry())
                && StringUtils.hasText(task.getEmploymentUnitNature())
                && StringUtils.hasText(task.getRegisteredAddressAreaCode())
                && StringUtils.hasText(task.getRegisteredAddressAreaName());
    }

    private void updateJobStatus(EmploymentJobEntity job) {
        List<EmploymentCompanyTaskEntity> tasks =
                companyRepository.findByJobIdOrderByInputCompanyNameAsc(job.getId());
        EmploymentJobStatus status;
        if (!readStringList(job.getValidationErrorsJson()).isEmpty()) {
            status = EmploymentJobStatus.HAS_VALIDATION_ERRORS;
        } else if (tasks.stream().allMatch(task -> EmploymentCompanyStatus.RESOLVED.name().equals(task.getStatus()))) {
            status = EmploymentJobStatus.READY_TO_EXPORT;
        } else {
            status = EmploymentJobStatus.IN_PROGRESS;
        }
        job.setStatus(status.name());
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private EmploymentJobEntity requireJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("批量任务不存在：" + jobId));
    }

    private EmploymentCompanyTaskEntity requireTask(String jobId, Long companyId) {
        return companyRepository.findByIdAndJobId(companyId, jobId)
                .orElseThrow(() -> new IllegalArgumentException("单位任务不存在：" + companyId));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的 Excel 模板");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("文件不能超过 20MB");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("目前只支持 .xlsx 格式");
        }
    }

    private String safeFilename(String filename) {
        String value = StringUtils.hasText(filename) ? Path.of(filename).getFileName().toString() : "就业数据.xlsx";
        return value.replaceAll("[\\r\\n]", "_");
    }

    private String removeXlsxSuffix(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                ? filename.substring(0, filename.length() - 5) : filename;
    }

    private Path storageRoot() {
        return properties.getStorageDir().toAbsolutePath().normalize();
    }

    private Path safeJobDirectory(String jobId) {
        if (jobId == null || !jobId.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("任务编号格式无效");
        }
        Path directory = storageRoot().resolve(jobId).normalize();
        ensureWithinRoot(directory);
        return directory;
    }

    private void ensureWithinRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot())) {
            throw new IllegalStateException("任务文件路径超出允许范围");
        }
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        ensureWithinRoot(directory);
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::tryDelete);
        } catch (IOException ignored) {
            // Cleanup is best-effort; a later scheduled run can retry.
        }
    }

    private void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstTemplateValue(String preferred, String suggested, Set<String> options) {
        if (StringUtils.hasText(preferred) && options.contains(preferred.trim())) {
            return preferred.trim();
        }
        return StringUtils.hasText(suggested) && options.contains(suggested.trim())
                ? suggested.trim() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("任务状态序列化失败", ex);
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, String> readStringMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    public record ExportedFile(Path path, String filename) {
    }
}
