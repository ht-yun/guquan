package com.guquan.equity.api;

import com.guquan.equity.model.CompanyProfile;
import java.util.List;
import java.util.Optional;

public interface CompanyInfoProvider {

    String providerName();

    List<CompanyProfile> searchByName(String companyName);

    default Optional<CompanyProfile> getByCreditCode(String creditCode) {
        return Optional.empty();
    }
}
