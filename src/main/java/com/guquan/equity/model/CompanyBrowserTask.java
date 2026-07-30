package com.guquan.equity.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyBrowserTask {

    private String taskId;
    private String companyName;
    private String creditCode;
    private String jobId;
    private Long currentCompanyId;
    private CompanyBrowserTaskStatus status;
    private String message;
    private String currentUrl;
    private String screenshotPath;
    private List<CompanyProfile> candidates;
    private Map<CompanyInfoSection, String> sectionStatuses;
    private int totalCompanies;
    private int completedCompanies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
