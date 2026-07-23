package com.guquan.equity.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentCompanyTaskRepository extends JpaRepository<EmploymentCompanyTaskEntity, Long> {

    List<EmploymentCompanyTaskEntity> findByJobIdOrderByInputCompanyNameAsc(String jobId);

    Optional<EmploymentCompanyTaskEntity> findByIdAndJobId(Long id, String jobId);

    void deleteByJobId(String jobId);
}
