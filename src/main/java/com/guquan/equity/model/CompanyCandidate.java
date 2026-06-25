package com.guquan.equity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCandidate {

    private String companyName;

    private String creditCode;

    private String legalPerson;

    private String status;

    private Boolean listedCompany;

    private String stockCode;

    private String source;
}
