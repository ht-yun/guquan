package com.guquan.equity.model;

import lombok.Data;

@Data
public class CompanyBatchConfirmRequest {
    private CompanyProfile profile;
    private boolean noRecord;
}
