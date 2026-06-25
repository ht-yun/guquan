package com.guquan.equity.model;

import com.guquan.equity.repository.EquityQueryRecordEntity;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EquityQueryRecordResponse {

    private String queryId;

    private String personName;

    private String companyName;

    private String creditCode;

    private String matchedCompanyName;

    private String matchedCreditCode;

    private QueryStatus status;

    private String confidence;

    private String resultJson;

    private String dataSource;

    private String operatorId;

    private String queryReason;

    private LocalDateTime createdAt;

    public static EquityQueryRecordResponse from(EquityQueryRecordEntity entity) {
        return EquityQueryRecordResponse.builder()
                .queryId(entity.getQueryId())
                .personName(entity.getPersonName())
                .companyName(entity.getCompanyName())
                .phoneNumber(entity.getPhoneNumber())
                .idNumber(entity.getIdNumber())
                .creditCode(entity.getCreditCode())
                .matchedCompanyName(entity.getMatchedCompanyName())
                .matchedCreditCode(entity.getMatchedCreditCode())
                .status(entity.getStatus())
                .confidence(entity.getConfidence())
                .resultJson(entity.getResultJson())
                .dataSource(entity.getDataSource())
                .operatorId(entity.getOperatorId())
                .queryReason(entity.getQueryReason())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String phoneNumber;

    private String idNumber;
}
