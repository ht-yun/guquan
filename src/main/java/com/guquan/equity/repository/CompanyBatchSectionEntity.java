package com.guquan.equity.repository;

import com.guquan.equity.model.CompanyInfoSection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "company_batch_section")
public class CompanyBatchSectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String jobId;

    @Column(nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanyInfoSection section;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(columnDefinition = "CLOB")
    private String rawText;

    @Column(length = 1000)
    private String sourceUrl;

    @Column(columnDefinition = "CLOB")
    private String parsedJson;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
