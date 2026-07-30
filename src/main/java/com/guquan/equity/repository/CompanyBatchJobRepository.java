package com.guquan.equity.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyBatchJobRepository extends JpaRepository<CompanyBatchJobEntity, String> {
    List<CompanyBatchJobEntity> findTop100ByOrderByCreatedAtDesc();
}
