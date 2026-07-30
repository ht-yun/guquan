package com.guquan.equity.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanySectionView {
    private CompanyInfoSection section;
    private String status;
    private String rawText;
    private String sourceUrl;
    private List<Map<String, String>> records;
    private LocalDateTime updatedAt;
}
