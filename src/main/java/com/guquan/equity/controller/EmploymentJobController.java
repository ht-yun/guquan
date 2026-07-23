package com.guquan.equity.controller;

import com.guquan.equity.model.EmploymentAreaOption;
import com.guquan.equity.model.EmploymentCompanyTaskView;
import com.guquan.equity.model.EmploymentConfirmRequest;
import com.guquan.equity.model.EmploymentImportPreview;
import com.guquan.equity.model.EmploymentImportRequest;
import com.guquan.equity.model.EmploymentJobSummary;
import com.guquan.equity.service.EmploymentJobService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employment-jobs")
public class EmploymentJobController {

    private final EmploymentJobService service;

    public EmploymentJobController(EmploymentJobService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmploymentJobSummary create(@RequestPart("file") MultipartFile file) {
        return service.create(file);
    }

    @GetMapping("/{jobId}")
    public EmploymentJobSummary get(@PathVariable String jobId) {
        return service.get(jobId);
    }

    @GetMapping("/{jobId}/companies")
    public List<EmploymentCompanyTaskView> companies(@PathVariable String jobId) {
        return service.companies(jobId);
    }

    @GetMapping("/{jobId}/areas")
    public List<EmploymentAreaOption> areas(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "") String query) {
        return service.searchAreas(jobId, query);
    }

    @PostMapping("/{jobId}/companies/{companyId}/import")
    public EmploymentImportPreview importPage(
            @PathVariable String jobId,
            @PathVariable Long companyId,
            @RequestBody EmploymentImportRequest request) {
        return service.importPage(jobId, companyId, request == null ? null : request.getPageText());
    }

    @PostMapping("/{jobId}/companies/{companyId}/collect")
    public EmploymentCompanyTaskView collect(
            @PathVariable String jobId,
            @PathVariable Long companyId,
            @RequestBody EmploymentConfirmRequest request) {
        return service.collect(jobId, companyId, request);
    }

    @PostMapping("/{jobId}/companies/{companyId}/confirm")
    public EmploymentCompanyTaskView confirm(
            @PathVariable String jobId,
            @PathVariable Long companyId,
            @RequestBody EmploymentConfirmRequest request) {
        return service.confirm(jobId, companyId, request);
    }

    @GetMapping("/{jobId}/export")
    public ResponseEntity<Resource> export(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "final") String mode) throws IOException {
        EmploymentJobService.ExportedFile exported = service.export(jobId, mode);
        FileSystemResource resource = new FileSystemResource(exported.path());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @DeleteMapping("/{jobId}")
    public Map<String, Object> delete(@PathVariable String jobId) {
        service.delete(jobId);
        return Map.of("jobId", jobId, "deleted", true);
    }
}
