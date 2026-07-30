package com.guquan.equity.repository;

import com.guquan.equity.model.CompanyInfoSection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyBatchSectionRepository extends JpaRepository<CompanyBatchSectionEntity, Long> {
    Optional<CompanyBatchSectionEntity> findByJobIdAndCompanyIdAndSection(String jobId, Long companyId, CompanyInfoSection section);
    List<CompanyBatchSectionEntity> findByJobIdOrderByCompanyIdAscIdAsc(String jobId);
    void deleteByJobId(String jobId);
}
