package com.guquan.equity.model;

public enum CompanyInfoSection {
    BASIC,
    SHAREHOLDERS,
    INVESTMENTS,
    POSITIONS,
    ABNORMAL,
    SERIOUS_VIOLATIONS;

    public static CompanyInfoSection parse(String value) {
        try { return valueOf(value.trim().toUpperCase().replace('-', '_')); }
        catch (Exception e) { throw new IllegalArgumentException("不支持的企业信息栏目：" + value); }
    }
}
