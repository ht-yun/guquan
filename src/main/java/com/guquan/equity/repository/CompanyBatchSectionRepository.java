package com.guquan.equity.repository;

import com.guquan.equity.model.CompanyInfoSection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyBatchSectionRepository extends JpaRepository<CompanyBatchSectionEntity, Long> {
    Optional<CompanyBatchSectionEntity> findByJobIdAndCompanyIdAndSection(String jobId, Long companyId, CompanyInfoSection section);
}
