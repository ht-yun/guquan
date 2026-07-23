package com.guquan.equity.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentRowRepository extends JpaRepository<EmploymentRowEntity, Long> {

    List<EmploymentRowEntity> findByCompanyTaskIdOrderByRowNumberAsc(Long companyTaskId);

    List<EmploymentRowEntity> findByJobIdOrderByRowNumberAsc(String jobId);

    void deleteByJobId(String jobId);
}
