package com.guquan.equity.model;

import java.util.List;
import lombok.Data;

@Data
public class EquityQueryRequest {

    private String personName;

    private String companyName;

    private String creditCode;

    private List<EquityRatioType> ratioTypes;

    private String operatorId;

    private String queryReason;
}
