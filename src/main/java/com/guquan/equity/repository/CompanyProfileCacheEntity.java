package com.guquan.equity.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "company_profile_cache")
public class CompanyProfileCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String companyName;

    @Column(length = 255)
    private String normalizedCompanyName;

    @Column(length = 64, unique = true)
    private String creditCode;

    @Column(length = 100)
    private String legalPerson;

    @Column(length = 50)
    private String registrationStatus;

    @Column(length = 1000)
    private String industryName;

    @Column(length = 50)
    private String industryCode;

    @Column(length = 255)
    private String entityType;

    @Column(length = 100)
    private String employmentIndustry;

    @Column(length = 100)
    private String employmentUnitNature;

    @Column(length = 50)
    private String classificationSource;

    @Column(length = 20)
    private String classificationConfidence;

    @Column(length = 255)
    private String registrationAuthority;

    @Column(length = 12)
    private String registrationAuthorityCode;

    @Column(length = 50)
    private String province;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String district;

    @Column(length = 12)
    private String provinceCode;

    @Column(length = 12)
    private String cityCode;

    @Column(length = 12)
    private String districtCode;

    @Column(length = 12)
    private String areaCode;

    @Column(length = 12)
    private String registeredAddressAreaCode;

    @Column(length = 20)
    private String areaCodeSource;

    @Column(length = 500)
    private String registeredAddress;

    @Column(length = 100)
    private String source;

    private LocalDate sourceUpdatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
