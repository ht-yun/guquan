package com.guquan.equity.model;

import lombok.Data;

/**
 */
@Data
public class PersonSearchRequest {

    /** 人员姓名（必填） */
    private String personName;

    /** 手机号（可选：用于未来商业数据源消歧） */
    private String phoneNumber;

    /** 身份证号（可选：用于未来商业数据源消歧） */
    private String idNumber;

    /** 公司名称（可选：限定搜索范围到某家公司） */
    private String companyName;

    private String operatorId;

    private String queryReason;

    /**
     * 数据源选择。
     * 可选值：null/"auto"（默认）、"eastmoney"（东方财富）、"qichacha"（企查查）、"all"（全部合并）
     */
    private String dataSource;
}
