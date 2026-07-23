package com.guquan.equity.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmploymentJobSummary {

    private String jobId;
    private String originalFilename;
    private EmploymentJobStatus status;
    private int totalRecords;
    private int uniqueCompanies;
    private int cacheHits;
    private int pendingCollection;
    private int pendingReview;
    private int conflicts;
    private int resolved;
    private List<String> validationErrors;
    private List<String> industryOptions;
    private List<String> unitNatureOptions;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
