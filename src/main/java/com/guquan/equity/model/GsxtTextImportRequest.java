package com.guquan.equity.model;

import lombok.Data;
import java.util.List;

@Data
public class GsxtTextImportRequest {

    private String companyName;
    private String pageText;

    private List<CompanyProfile> profiles;
}
