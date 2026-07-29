package com.guquan.equity.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyBatchJobView {
    private String jobId;
    private String originalFilename;
    private int totalCompanies;
    private int cacheHits;
    private int pendingCollection;
    private int pendingReview;
    private int conflicts;
    private int resolved;
    private LocalDateTime createdAt;
}
