package com.guquan.equity.model;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyAllSectionsView {
    private Map<CompanyInfoSection, CompanySectionView> sections;
    private LocalDateTime updatedAt;
}
