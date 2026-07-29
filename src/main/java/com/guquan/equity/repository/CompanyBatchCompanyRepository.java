package com.guquan.equity.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyBatchCompanyRepository extends JpaRepository<CompanyBatchCompanyEntity, Long> {
    List<CompanyBatchCompanyEntity> findByJobIdOrderById(String jobId);
    void deleteByJobId(String jobId);
}
