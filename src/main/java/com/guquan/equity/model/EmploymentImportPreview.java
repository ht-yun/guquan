package com.guquan.equity.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmploymentImportPreview {

    private int candidateCount;
    private List<EmploymentCandidateView> candidates;
    private String message;
}
