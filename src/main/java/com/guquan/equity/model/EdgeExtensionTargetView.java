package com.guquan.equity.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EdgeExtensionTargetView {
    private String jobId;
    private Long companyId;
    private String companyName;
    private String creditCode;
}
