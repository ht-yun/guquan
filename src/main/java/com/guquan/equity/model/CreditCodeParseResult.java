package com.guquan.equity.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreditCodeParseResult {

    private String creditCode;

    private boolean valid;

    private String message;

    private String organizationTypeCode;

    private String registrationAuthorityCode;

    private String organizationCode;

    private String checkCode;
}
