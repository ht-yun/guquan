package com.guquan.equity.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyOtherInformationItem {
    private Long companyId;
    private String inputCompanyName;
    private String officialCompanyName;
    private String creditCode;
    private CompanyInfoSection section;
    private String sectionStatus;
    private int recordNumber;
    private String fieldName;
    private String value;
}
