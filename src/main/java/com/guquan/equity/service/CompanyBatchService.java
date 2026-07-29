package com.guquan.equity.service;

import com.guquan.equity.api.CompanyProfileCacheService;
import com.guquan.equity.model.CompanyBatchCompanyView;
import com.guquan.equity.model.CompanyBatchConfirmRequest;
import com.guquan.equity.model.CompanyBatchImportRequest;
import com.guquan.equity.model.CompanyBatchJobView;
import com.guquan.equity.model.CompanyBatchStatus;
import com.guquan.equity.model.CompanyAllSectionsView;
import com.guquan.equity.model.CompanyBrowserTaskRequest;
import com.guquan.equity.model.CompanyInfoSection;
import com.guquan.equity.model.CompanySectionImportRequest;
import com.guquan.equity.model.CompanySectionView;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.repository.CompanyBatchSectionEntity;
import com.guquan.equity.repository.CompanyBatchSectionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guquan.equity.repository.CompanyBatchCompanyEntity;
import com.guquan.equity.repository.CompanyBatchCompanyRepository;
import com.guquan.equity.repository.CompanyBatchJobEntity;
import com.guquan.equity.repository.CompanyBatchJobRepository;
import com.guquan.equity.util.CompanyNameNormalizer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CompanyBatchService {
    private final CompanyBatchJobRepository jobs;
    private final CompanyBatchCompanyRepository companies;
    private final CompanyProfileCacheService cache;
    private final GsxtPageParser parser;
    private final CompanyBatchSectionRepository sections;
    private final CompanySectionParser sectionParser;
    private final CompanyAllSectionsParser allSectionsParser;
    private final ObjectMapper objectMapper;

    public CompanyBatchService(CompanyBatchJobRepository jobs, CompanyBatchCompanyRepository companies,
            CompanyProfileCacheService cache, GsxtPageParser parser, CompanyBatchSectionRepository sections,
            CompanySectionParser sectionParser, CompanyAllSectionsParser allSectionsParser, ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.companies = companies;
        this.cache = cache;
        this.parser = parser;
        this.sections = sections;
        this.sectionParser = sectionParser;
        this.allSectionsParser = allSectionsParser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CompanyBatchJobView create(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择企业名单文件");
        List<String> names = readNames(file);
        if (names.isEmpty()) throw new IllegalArgumentException("名单中没有可用的企业名称");
        String jobId = UUID.randomUUID().toString();
        CompanyBatchJobEntity job = new CompanyBatchJobEntity();
        job.setId(jobId); job.setOriginalFilename(file.getOriginalFilename() == null ? "企业名单" : file.getOriginalFilename());
        job.setCreatedAt(LocalDateTime.now()); jobs.save(job);
        Map<String, String> unique = new LinkedHashMap<>();
        for (String name : names) unique.putIfAbsent(CompanyNameNormalizer.normalize(name), name);
        for (Map.Entry<String, String> item : unique.entrySet()) {
            CompanyBatchCompanyEntity task = new CompanyBatchCompanyEntity();
            task.setJobId(jobId); task.setInputCompanyName(item.getValue()); task.setNormalizedCompanyName(item.getKey());
            task.setStatus(CompanyBatchStatus.NEEDS_COLLECTION); task.setUpdatedAt(LocalDateTime.now());
            cache.findExact(item.getValue()).ifPresent(profile -> { apply(task, profile); task.setStatus(CompanyBatchStatus.NEEDS_REVIEW); });
            companies.save(task);
        }
        return get(jobId);
    }

    @Transactional
    public CompanyBatchJobView createManual(String companyName) {
        if (!StringUtils.hasText(companyName)) throw new IllegalArgumentException("请输入公司/单位名称");
        String jobId = UUID.randomUUID().toString();
        CompanyBatchJobEntity job = new CompanyBatchJobEntity();
        job.setId(jobId); job.setOriginalFilename("单企业查询"); job.setCreatedAt(LocalDateTime.now()); jobs.save(job);
        CompanyBatchCompanyEntity task = new CompanyBatchCompanyEntity();
        task.setJobId(jobId); task.setInputCompanyName(companyName.trim());
        task.setNormalizedCompanyName(CompanyNameNormalizer.normalize(companyName));
        task.setStatus(CompanyBatchStatus.NEEDS_COLLECTION); task.setUpdatedAt(LocalDateTime.now());
        cache.findExact(companyName.trim()).ifPresent(profile -> { apply(task, profile); task.setStatus(CompanyBatchStatus.NEEDS_REVIEW); });
        companies.save(task);
        return get(jobId);
    }

    public CompanyBatchJobView get(String jobId) {
        requireJob(jobId);
        List<CompanyBatchCompanyEntity> list = companies.findByJobIdOrderById(jobId);
        return CompanyBatchJobView.builder().jobId(jobId).originalFilename(jobs.findById(jobId).orElseThrow().getOriginalFilename())
                .totalCompanies(list.size()).cacheHits((int) list.stream().filter(CompanyBatchCompanyEntity::isCacheHit).count())
                .pendingCollection(count(list, CompanyBatchStatus.NEEDS_COLLECTION)).pendingReview(count(list, CompanyBatchStatus.NEEDS_REVIEW))
                .conflicts(count(list, CompanyBatchStatus.CONFLICT)).resolved(count(list, CompanyBatchStatus.RESOLVED))
                .createdAt(jobs.findById(jobId).orElseThrow().getCreatedAt()).build();
    }

    public List<CompanyBatchCompanyView> companies(String jobId) {
        requireJob(jobId); return this.companies.findByJobIdOrderById(jobId).stream().map(this::view).toList();
    }

    @Transactional
    public List<CompanyProfile> importPage(String jobId, Long companyId, CompanyBatchImportRequest request) {
        CompanyBatchCompanyEntity task = requireCompany(jobId, companyId);
        if (request == null || !StringUtils.hasText(request.getPageText())) throw new IllegalArgumentException("请粘贴官网页面文字");
        CompanyBrowserTaskRequest parserRequest = new CompanyBrowserTaskRequest(); parserRequest.setCompanyName(task.getInputCompanyName());
        List<CompanyProfile> profiles = parser.parse(request.getPageText(), parserRequest, "GSXT_MANUAL_IMPORT");
        if (profiles.size() == 1) apply(task, profiles.get(0));
        task.setStatus(profiles.isEmpty() ? CompanyBatchStatus.NEEDS_COLLECTION : CompanyBatchStatus.NEEDS_REVIEW);
        task.setUpdatedAt(LocalDateTime.now()); companies.save(task); return profiles;
    }

    @Transactional
    public CompanyBatchCompanyView confirm(String jobId, Long companyId, CompanyBatchConfirmRequest request) {
        CompanyBatchCompanyEntity task = requireCompany(jobId, companyId);
        if (request == null || request.isNoRecord()) { task.setStatus(CompanyBatchStatus.RESOLVED); task.setUpdatedAt(LocalDateTime.now()); companies.save(task); return view(task); }
        if (request.getProfile() == null || !StringUtils.hasText(request.getProfile().getCompanyName())
                || !StringUtils.hasText(request.getProfile().getCreditCode())) throw new IllegalArgumentException("请至少确认企业名称和统一社会信用代码");
        CompanyProfile saved = cache.save(request.getProfile()); apply(task, saved); task.setStatus(CompanyBatchStatus.RESOLVED); task.setUpdatedAt(LocalDateTime.now()); companies.save(task); return view(task);
    }

    @Transactional
    public CompanyAllSectionsView previewAllSections(String jobId, Long companyId, String text) {
        requireCompany(jobId, companyId);
        return allSectionsView(allSectionsParser.parse(text), text, false, jobId, companyId);
    }

    @Transactional
    public CompanyAllSectionsView confirmAllSections(String jobId, Long companyId, String text) {
        requireCompany(jobId, companyId);
        CompanyBatchCompanyEntity task = requireCompany(jobId, companyId);
        CompanyBrowserTaskRequest parserRequest = new CompanyBrowserTaskRequest();
        parserRequest.setCompanyName(task.getInputCompanyName());
        List<CompanyProfile> profiles = parser.parse(text, parserRequest, "GSXT_MANUAL_IMPORT");
        if (profiles.size() == 1) {
            CompanyProfile saved = cache.save(profiles.get(0));
            apply(task, saved);
        }
        task.setStatus(profiles.size() == 1 ? CompanyBatchStatus.RESOLVED : CompanyBatchStatus.NEEDS_REVIEW);
        task.setUpdatedAt(LocalDateTime.now());
        companies.save(task);
        return allSectionsView(allSectionsParser.parse(text), text, true, jobId, companyId);
    }

    @Transactional
    public CompanySectionView previewSection(String jobId, Long companyId, String sectionName,
            CompanySectionImportRequest request) {
        requireCompany(jobId, companyId);
        CompanyInfoSection section = CompanyInfoSection.parse(sectionName);
        CompanySectionParser.ParsedSection parsed = sectionParser.parse(section, request == null ? null : request.getPageText());
        return CompanySectionView.builder().section(section).status(parsed.status()).rawText(request == null ? null : request.getPageText())
                .records(parsed.records()).updatedAt(LocalDateTime.now()).build();
    }

    @Transactional
    public CompanySectionView confirmSection(String jobId, Long companyId, String sectionName,
            CompanySectionImportRequest request) {
        requireCompany(jobId, companyId);
        CompanyInfoSection section = CompanyInfoSection.parse(sectionName);
        CompanySectionParser.ParsedSection parsed = sectionParser.parse(section, request == null ? null : request.getPageText());
        CompanyBatchSectionEntity entity = sections.findByJobIdAndCompanyIdAndSection(jobId, companyId, section)
                .orElseGet(CompanyBatchSectionEntity::new);
        entity.setJobId(jobId); entity.setCompanyId(companyId); entity.setSection(section); entity.setStatus(parsed.status());
        entity.setRawText(request == null ? null : request.getPageText()); entity.setParsedJson(writeJson(parsed.records())); entity.setUpdatedAt(LocalDateTime.now());
        sections.save(entity);
        return sectionView(entity);
    }

    public List<CompanySectionView> sectionViews(String jobId, Long companyId) {
        requireCompany(jobId, companyId);
        return java.util.Arrays.stream(CompanyInfoSection.values()).map(section -> sections
                .findByJobIdAndCompanyIdAndSection(jobId, companyId, section).map(this::sectionView)
                .orElseGet(() -> CompanySectionView.builder().section(section).status("NOT_COLLECTED").records(List.of()).build())).toList();
    }

    public byte[] export(String jobId) {
        List<CompanyBatchCompanyEntity> list = companies.findByJobIdOrderById(requireJob(jobId).getId());
        StringBuilder out = new StringBuilder("单位名称,工商登记全称,统一社会信用代码,法定代表人,经营状态,行业,企业类型,注册地址,状态\n");
        for (CompanyBatchCompanyEntity c : list) out.append(csv(c.getInputCompanyName())).append(',').append(csv(c.getOfficialCompanyName())).append(',').append(csv(c.getCreditCode())).append(',').append(csv(c.getLegalPerson())).append(',').append(csv(c.getRegistrationStatus())).append(',').append(csv(c.getIndustryName())).append(',').append(csv(c.getEntityType())).append(',').append(csv(c.getRegisteredAddress())).append(',').append(c.getStatus()).append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void delete(String jobId) {
        requireJob(jobId);
        companies.deleteByJobId(jobId);
        jobs.deleteById(jobId);
    }

    private List<String> readNames(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try { return filename.endsWith(".csv") ? readCsv(file) : readXlsx(file); }
        catch (IOException e) { throw new IllegalArgumentException("读取名单失败：" + e.getMessage(), e); }
    }
    private List<String> readCsv(MultipartFile file) throws IOException {
        List<String> result = new ArrayList<>(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) { String line; while ((line = reader.readLine()) != null) { String value = line.split(",", -1)[0].replace("\uFEFF", "").trim(); if (!isHeader(value) && StringUtils.hasText(value)) result.add(value); } } return result;
    }
    private List<String> readXlsx(MultipartFile file) throws IOException {
        List<String> result = new ArrayList<>(); try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) { if (workbook.getNumberOfSheets() == 0) return result; var sheet = workbook.getSheetAt(0); DataFormatter formatter = new DataFormatter(); int column = 0; Row header = sheet.getRow(0); if (header != null) for (var cell : header) { String v = formatter.formatCellValue(cell).trim(); if (v.contains("单位名称") || v.contains("企业名称") || v.contains("公司名称")) { column = cell.getColumnIndex(); break; } } for (int i = 0; i <= sheet.getLastRowNum(); i++) { Row row = sheet.getRow(i); if (row == null || row.getCell(column) == null) continue; String value = formatter.formatCellValue(row.getCell(column)).trim(); if (!isHeader(value) && StringUtils.hasText(value)) result.add(value); } } return result;
    }
    private boolean isHeader(String value) { return value.equals("单位名称") || value.equals("企业名称") || value.equals("公司名称"); }
    private int count(List<CompanyBatchCompanyEntity> list, CompanyBatchStatus status) { return (int) list.stream().filter(item -> item.getStatus() == status).count(); }
    private void apply(CompanyBatchCompanyEntity task, CompanyProfile profile) { task.setCacheHit(task.isCacheHit() || task.getStatus() == CompanyBatchStatus.NEEDS_COLLECTION); task.setOfficialCompanyName(profile.getCompanyName()); task.setCreditCode(profile.getCreditCode()); task.setLegalPerson(profile.getLegalPerson()); task.setRegistrationStatus(profile.getRegistrationStatus()); task.setIndustryName(profile.getIndustryName()); task.setEntityType(profile.getEntityType()); task.setRegisteredAddress(profile.getRegisteredAddress()); task.setRegisteredAddressAreaCode(profile.getRegisteredAddressAreaCode()); task.setSource(profile.getSource()); }
    private CompanyBatchCompanyView view(CompanyBatchCompanyEntity c) { CompanyProfile p = CompanyProfile.builder().companyName(c.getOfficialCompanyName()).creditCode(c.getCreditCode()).legalPerson(c.getLegalPerson()).registrationStatus(c.getRegistrationStatus()).industryName(c.getIndustryName()).entityType(c.getEntityType()).registeredAddress(c.getRegisteredAddress()).registeredAddressAreaCode(c.getRegisteredAddressAreaCode()).source(c.getSource()).build(); return CompanyBatchCompanyView.builder().companyId(c.getId()).inputCompanyName(c.getInputCompanyName()).normalizedCompanyName(c.getNormalizedCompanyName()).status(c.getStatus()).cacheHit(c.isCacheHit()).profile(p).updatedAt(c.getUpdatedAt()).build(); }
    private CompanyBatchJobEntity requireJob(String id) { return jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("企业任务不存在")); }
    private CompanyBatchCompanyEntity requireCompany(String jobId, Long id) { CompanyBatchCompanyEntity c = companies.findById(id).orElseThrow(() -> new IllegalArgumentException("企业任务不存在")); if (!c.getJobId().equals(jobId)) throw new IllegalArgumentException("企业任务不属于当前批次"); return c; }
    private String csv(String value) { String v = value == null ? "" : value.replace("\"", "\"\""); return "\"" + v + "\""; }
    private String writeJson(List<Map<String, String>> records) { try { return objectMapper.writeValueAsString(records); } catch (Exception e) { throw new IllegalStateException("保存栏目解析结果失败", e); } }
    private CompanySectionView sectionView(CompanyBatchSectionEntity entity) { try { return CompanySectionView.builder().section(entity.getSection()).status(entity.getStatus()).rawText(entity.getRawText()).records(objectMapper.readValue(entity.getParsedJson(), new TypeReference<List<Map<String, String>>>() {})).updatedAt(entity.getUpdatedAt()).build(); } catch (Exception e) { throw new IllegalStateException("读取栏目解析结果失败", e); } }
    private CompanyAllSectionsView allSectionsView(Map<CompanyInfoSection, CompanySectionParser.ParsedSection> parsed,
            String rawText, boolean save, String jobId, Long companyId) {
        Map<CompanyInfoSection, CompanySectionView> views = new java.util.EnumMap<>(CompanyInfoSection.class);
        for (Map.Entry<CompanyInfoSection, CompanySectionParser.ParsedSection> item : parsed.entrySet()) {
            CompanyInfoSection section = item.getKey(); CompanySectionParser.ParsedSection value = item.getValue();
            if (save) {
                CompanyBatchSectionEntity entity = sections.findByJobIdAndCompanyIdAndSection(jobId, companyId, section)
                        .orElseGet(CompanyBatchSectionEntity::new);
                entity.setJobId(jobId); entity.setCompanyId(companyId); entity.setSection(section); entity.setStatus(value.status());
                entity.setRawText(rawText); entity.setParsedJson(writeJson(value.records())); entity.setUpdatedAt(LocalDateTime.now()); sections.save(entity);
            }
            views.put(section, CompanySectionView.builder().section(section).status(value.status()).rawText(rawText)
                    .records(value.records()).updatedAt(LocalDateTime.now()).build());
        }
        return CompanyAllSectionsView.builder().sections(views).updatedAt(LocalDateTime.now()).build();
    }
}
