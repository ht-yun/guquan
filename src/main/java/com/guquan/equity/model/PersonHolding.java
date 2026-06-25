package com.guquan.equity.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人员在某个上市公司中的持股信息（带公司上下文）。
 * 用于"人→公司"维度的跨公司持股搜索。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonHolding {

    /** 公司名称 */
    private String companyName;

    /** 股票代码（如 600519.SH） */
    private String creditCode;

    /** 股东名称 */
    private String shareholderName;

    /** 股东类型：十大股东 / 十大流通股东 */
    private String shareholderType;

    /** 持股数量 */
    private BigDecimal holdingAmount;

    /** 持股比例 */
    private BigDecimal holdingRatio;

    /** 币种/单位 */
    private String currency;

    /** 数据来源 */
    private String dataSource;

    /** 报告期 */
    private LocalDate sourceUpdatedAt;
}
