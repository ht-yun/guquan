package com.guquan.equity.model;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfile {

    private String companyName;

    private String creditCode;

    private String legalPerson;

    private String registrationStatus;

    private String industryName;

    private String industryCode;

    private String entityType;

    private String classificationSource;

    private String classificationConfidence;

    private String registrationAuthority;

    private String registrationAuthorityCode;

    private String province;

    private String city;

    private String district;

    private String provinceCode;

    private String cityCode;

    private String districtCode;

    private String areaCode;

    private String registeredAddressAreaCode;

    private String areaCodeSource;

    private String registeredAddress;

    private String source;

    private LocalDate sourceUpdatedAt;
}
