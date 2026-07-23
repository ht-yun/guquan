package com.guquan.equity.model;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class EmploymentConfirmRequest {

    private CompanyProfile profile;
    private String employmentIndustry;
    private String employmentUnitNature;
    private String registeredAddressAreaCode;
    private Boolean replaceOfficialName;
    private Map<String, String> conflictDecisions = new LinkedHashMap<>();
}
