package com.guquan.equity.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareholderHolding {

    private String shareholderName;

    private String shareholderType;

    private BigDecimal subscribedAmount;

    private BigDecimal subscribedRatio;

    private BigDecimal paidInAmount;

    private BigDecimal paidInRatio;

    private BigDecimal listedHoldingAmount;

    private BigDecimal listedHoldingRatio;

    private String currency;

    private String source;

    private LocalDate sourceUpdatedAt;
}
