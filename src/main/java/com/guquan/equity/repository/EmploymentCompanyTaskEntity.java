package com.guquan.equity.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "employment_company_task", uniqueConstraints =
        @UniqueConstraint(name = "uk_employment_company_job_name", columnNames = {"job_id", "normalized_name"}))
public class EmploymentCompanyTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 40)
    private String jobId;

    @Column(nullable = false, length = 255)
    private String inputCompanyName;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(length = 255)
    private String officialCompanyName;

    @Column(length = 64)
    private String creditCode;

    @Column(length = 100)
    private String rawIndustryName;

    @Column(length = 100)
    private String rawEntityType;

    @Column(length = 500)
    private String registeredAddress;

    @Column(length = 100)
    private String employmentIndustry;

    @Column(length = 100)
    private String employmentUnitNature;

    @Column(length = 12)
    private String registeredAddressAreaCode;

    @Column(length = 100)
    private String registeredAddressAreaName;

    @Column(length = 50)
    private String classificationSource;

    @Column(length = 20)
    private String classificationConfidence;

    @Column(length = 100)
    private String source;

    @Column(nullable = false, length = 30)
    private String status;

    private int affectedRows;
    private boolean cacheHit;
    private boolean replaceOfficialName;

    @Lob
    private String conflictFieldsJson;

    @Lob
    private String conflictDecisionsJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
