package com.guquan.equity.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyBrowserTask {

    private String taskId;
    private String companyName;
    private String creditCode;
    private CompanyBrowserTaskStatus status;
    private String message;
    private String currentUrl;
    private String screenshotPath;
    private List<CompanyProfile> candidates;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
