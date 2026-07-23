package com.guquan.equity.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmploymentCompanyTaskView {

    private Long companyId;
    private String inputCompanyName;
    private String officialCompanyName;
    private String creditCode;
    private String rawIndustryName;
    private String rawEntityType;
    private String registeredAddress;
    private String employmentIndustry;
    private String employmentUnitNature;
    private String registeredAddressAreaCode;
    private String registeredAddressAreaName;
    private String classificationSource;
    private String classificationConfidence;
    private String source;
    private EmploymentCompanyStatus status;
    private int affectedRows;
    private boolean cacheHit;
    private boolean replaceOfficialName;
    private List<Integer> rowNumbers;
    private List<String> conflictFields;
    private Map<String, String> conflictDecisions;
}
