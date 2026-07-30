package com.guquan.equity.controller;

import com.guquan.equity.api.CompanyBrowserLookupService;
import com.guquan.equity.api.CompanyProfileCacheService;
import com.guquan.equity.model.CompanyBrowserTask;
import com.guquan.equity.model.CompanyBrowserTaskRequest;
import com.guquan.equity.model.CompanyBrowserCandidateSelectionRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/company/browser")
public class CompanyBrowserLookupController {

    private final CompanyBrowserLookupService service;
    private final CompanyProfileCacheService cacheService;

    public CompanyBrowserLookupController(
            CompanyBrowserLookupService service, CompanyProfileCacheService cacheService) {
        this.service = service;
        this.cacheService = cacheService;
    }

    @PostMapping("/tasks")
    public CompanyBrowserTask start(@RequestBody CompanyBrowserTaskRequest request) {
        if (request == null || ((request.getCompanyName() == null || request.getCompanyName().isBlank())
                && (request.getCreditCode() == null || request.getCreditCode().isBlank()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入中国企业名称或统一社会信用代码");
        }
        return service.start(request);
    }

    @GetMapping("/tasks/{taskId}")
    public CompanyBrowserTask get(@PathVariable String taskId) {
        try {
            return service.get(taskId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping("/tasks/{taskId}/continue")
    public CompanyBrowserTask continueTask(@PathVariable String taskId) {
        try {
            return service.continueTask(taskId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping("/tasks/{taskId}/select")
    public CompanyBrowserTask selectCandidate(@PathVariable String taskId,
            @RequestBody CompanyBrowserCandidateSelectionRequest request) {
        try {
            return service.selectCandidate(taskId, request == null ? null : request.getCreditCode());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping("/tasks/{taskId}/save")
    public Map<String, Object> saveCandidates(@PathVariable String taskId) {
        CompanyBrowserTask task;
        try {
            task = service.get(taskId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
        if (task.getCandidates() == null || task.getCandidates().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务没有可保存的候选单位");
        }
        var saved = cacheService.saveAll(task.getCandidates());
        return Map.of("taskId", taskId, "savedCount", saved.size(), "profiles", saved);
    }

    @DeleteMapping("/tasks/{taskId}")
    public Map<String, Object> close(@PathVariable String taskId) {
        try {
            service.get(taskId);
            service.close(taskId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
        return Map.of("taskId", taskId, "closed", true);
    }
}
