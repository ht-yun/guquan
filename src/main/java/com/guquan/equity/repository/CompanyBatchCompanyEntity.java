package com.guquan.equity.repository;

import com.guquan.equity.model.CompanyBatchStatus;
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
@Table(name = "company_batch_company")
public class CompanyBatchCompanyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String jobId;

    @Column(nullable = false, length = 255)
    private String inputCompanyName;

    @Column(nullable = false, length = 255)
    private String normalizedCompanyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanyBatchStatus status;

    @Column(nullable = false)
    private boolean cacheHit;

    @Column(length = 64)
    private String creditCode;

    @Column(length = 255)
    private String officialCompanyName;

    @Column(length = 100)
    private String legalPerson;

    @Column(length = 50)
    private String registrationStatus;

    @Column(length = 1000)
    private String industryName;

    @Column(length = 255)
    private String entityType;

    @Column(length = 500)
    private String registeredAddress;

    @Column(length = 12)
    private String registeredAddressAreaCode;

    @Column(length = 100)
    private String source;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
