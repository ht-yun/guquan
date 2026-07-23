package com.guquan.equity.model;

import lombok.Data;

@Data
public class CompanyLookupRequest {

    private String companyName;

    private String creditCode;

    private String dataSource;

    private String operatorId;

    private String queryReason;
}
