package com.guquan.equity.api;

import com.guquan.equity.model.CompanyLookupRequest;
import com.guquan.equity.model.CompanyLookupResult;

public interface CompanyLookupService {

    CompanyLookupResult lookup(CompanyLookupRequest request);
}
