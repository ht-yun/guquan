 package com.guquan.equity.repository;
 
 import java.util.List;
 import java.util.Optional;
 import org.springframework.data.jpa.repository.JpaRepository;
 
 public interface CompanyProfileCacheRepository extends JpaRepository<CompanyProfileCacheEntity, Long> {
 
     List<CompanyProfileCacheEntity> findTop20ByCompanyNameContaining(String companyName);
 
     Optional<CompanyProfileCacheEntity> findByCreditCodeIgnoreCase(String creditCode);

     Optional<CompanyProfileCacheEntity> findFirstByCompanyNameIgnoreCase(String companyName);

     Optional<CompanyProfileCacheEntity> findFirstByNormalizedCompanyName(String normalizedCompanyName);

     List<CompanyProfileCacheEntity> findByCreditCodeInIgnoreCase(List<String> creditCodes);
 }
