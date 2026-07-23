 package com.guquan.equity.api;
 
 import com.guquan.equity.model.CompanyProfile;
 import com.guquan.equity.model.CreditCodeParseResult;
import java.util.List;
import java.util.Optional;
 
 public interface CompanyProfileCacheService {
 
     CompanyProfile save(CompanyProfile profile);
 
     List<CompanyProfile> saveAll(List<CompanyProfile> profiles);

    CreditCodeParseResult parseCreditCode(String creditCode);

    Optional<CompanyProfile> findExact(String companyName);

    void saveAlias(String aliasName, CompanyProfile profile);
}
