package com.guquan.equity.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EquityQueryResult {

    private String queryId;

    private String personName;

    private CompanyCandidate company;

    private List<CompanyCandidate> companyCandidates;

    private List<ShareholderHolding> holdings;

    private QueryStatus status;

    private String confidence;

    private String message;
}
