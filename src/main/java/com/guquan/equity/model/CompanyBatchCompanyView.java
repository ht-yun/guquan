package com.guquan.equity.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyBatchCompanyView {
    private Long companyId;
    private String inputCompanyName;
    private String normalizedCompanyName;
    private CompanyBatchStatus status;
    private boolean cacheHit;
    private CompanyProfile profile;
    private String collectionMessage;
    private LocalDateTime updatedAt;
}
