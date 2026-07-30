package com.guquan.equity.controller;

import com.guquan.equity.model.CompanyBatchCompanyView;
import com.guquan.equity.model.CompanyBatchConfirmRequest;
import com.guquan.equity.model.CompanyBatchImportRequest;
import com.guquan.equity.model.CompanyBatchJobView;
import com.guquan.equity.model.CompanyBatchCreateRequest;
import com.guquan.equity.model.CompanyAllSectionsView;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.CompanySectionImportRequest;
import com.guquan.equity.model.CompanySectionView;
import com.guquan.equity.model.CompanyBrowserCandidateSelectionRequest;
import com.guquan.equity.model.CompanyBrowserTask;
import com.guquan.equity.model.CompanyBrowserTaskRequest;
import com.guquan.equity.model.CompanyOtherInformationItem;
import com.guquan.equity.api.CompanyBrowserLookupService;
import com.guquan.equity.service.CompanyBatchService;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/company-batches")
public class CompanyBatchController {
    private final CompanyBatchService service;
    private final CompanyBrowserLookupService browserService;
    public CompanyBatchController(CompanyBatchService service, CompanyBrowserLookupService browserService) { this.service = service; this.browserService = browserService; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyBatchJobView create(@RequestPart("file") MultipartFile file) { return service.create(file); }
    @PostMapping("/manual") public CompanyBatchJobView createManual(@RequestBody CompanyBatchCreateRequest request) { return service.createManual(request == null ? null : request.getCompanyName()); }
    @GetMapping public List<CompanyBatchJobView> list() { return service.listJobs(); }
    @GetMapping("/{jobId}") public CompanyBatchJobView get(@PathVariable String jobId) { return service.get(jobId); }
    @GetMapping("/{jobId}/companies") public List<CompanyBatchCompanyView> companies(@PathVariable String jobId) { return service.companies(jobId); }
    @PostMapping("/{jobId}/companies/{companyId}/import") public List<CompanyProfile> importPage(@PathVariable String jobId, @PathVariable Long companyId, @RequestBody CompanyBatchImportRequest request) { return service.importPage(jobId, companyId, request); }
    @PostMapping("/{jobId}/companies/{companyId}/confirm") public CompanyBatchCompanyView confirm(@PathVariable String jobId, @PathVariable Long companyId, @RequestBody CompanyBatchConfirmRequest request) { return service.confirm(jobId, companyId, request); }
    @GetMapping("/{jobId}/companies/{companyId}/sections") public List<CompanySectionView> sections(@PathVariable String jobId, @PathVariable Long companyId) { return service.sectionViews(jobId, companyId); }
    @PostMapping("/{jobId}/companies/{companyId}/sections/{section}/preview") public CompanySectionView previewSection(@PathVariable String jobId, @PathVariable Long companyId, @PathVariable String section, @RequestBody CompanySectionImportRequest request) { return service.previewSection(jobId, companyId, section, request); }
    @PostMapping("/{jobId}/companies/{companyId}/sections/{section}/confirm") public CompanySectionView confirmSection(@PathVariable String jobId, @PathVariable Long companyId, @PathVariable String section, @RequestBody CompanySectionImportRequest request) { return service.confirmSection(jobId, companyId, section, request); }
    @PostMapping("/{jobId}/companies/{companyId}/all-sections/preview") public CompanyAllSectionsView previewAllSections(@PathVariable String jobId, @PathVariable Long companyId, @RequestBody CompanySectionImportRequest request) { return service.previewAllSections(jobId, companyId, request == null ? null : request.getPageText()); }
    @PostMapping("/{jobId}/companies/{companyId}/all-sections/confirm") public CompanyAllSectionsView confirmAllSections(@PathVariable String jobId, @PathVariable Long companyId, @RequestBody CompanySectionImportRequest request) { return service.confirmAllSections(jobId, companyId, request == null ? null : request.getPageText()); }
    @PostMapping("/{jobId}/browser/start") public CompanyBrowserTask startBrowser(@PathVariable String jobId) { CompanyBrowserTaskRequest request = new CompanyBrowserTaskRequest(); request.setJobId(jobId); return browserService.start(request); }
    @GetMapping("/{jobId}/browser/tasks/{taskId}") public CompanyBrowserTask browserTask(@PathVariable String jobId, @PathVariable String taskId) { return requireBrowserJob(jobId, browserService.get(taskId)); }
    @PostMapping("/{jobId}/browser/tasks/{taskId}/continue") public CompanyBrowserTask continueBrowser(@PathVariable String jobId, @PathVariable String taskId) { return requireBrowserJob(jobId, browserService.continueTask(taskId)); }
    @PostMapping("/{jobId}/browser/tasks/{taskId}/select") public CompanyBrowserTask selectBrowserCandidate(@PathVariable String jobId, @PathVariable String taskId, @RequestBody CompanyBrowserCandidateSelectionRequest request) { return requireBrowserJob(jobId, browserService.selectCandidate(taskId, request == null ? null : request.getCreditCode())); }
    @DeleteMapping("/{jobId}/browser/tasks/{taskId}") public void stopBrowser(@PathVariable String jobId, @PathVariable String taskId) { requireBrowserJob(jobId, browserService.get(taskId)); browserService.close(taskId); }
    @PostMapping("/{jobId}/companies/{companyId}/skip") public void skipCompany(@PathVariable String jobId, @PathVariable Long companyId) { service.markAutomationIssue(jobId, companyId, "已由用户跳过，等待人工处理"); }
    @GetMapping("/{jobId}/export") public ResponseEntity<ByteArrayResource> export(@PathVariable String jobId) { byte[] bytes = service.export(jobId); HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8")); headers.setContentDisposition(ContentDisposition.attachment().filename("企业查询结果.csv").build()); return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(bytes)); }
    @GetMapping("/{jobId}/other-information") public List<CompanyOtherInformationItem> otherInformation(@PathVariable String jobId) { return service.otherInformation(jobId); }
    @GetMapping("/{jobId}/export/other-information") public ResponseEntity<ByteArrayResource> exportOtherInformation(@PathVariable String jobId) { byte[] bytes = service.exportOtherInformation(jobId); HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8")); headers.setContentDisposition(ContentDisposition.attachment().filename("企业查询其余信息清单.csv").build()); return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(bytes)); }
    @DeleteMapping("/{jobId}") public void delete(@PathVariable String jobId) { service.delete(jobId); }
    private CompanyBrowserTask requireBrowserJob(String jobId, CompanyBrowserTask task) { if (!jobId.equals(task.getJobId())) throw new IllegalArgumentException("浏览器任务不属于当前批次"); return task; }
}
