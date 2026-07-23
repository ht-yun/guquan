package com.guquan.equity.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentJobRepository extends JpaRepository<EmploymentJobEntity, String> {

    List<EmploymentJobEntity> findByExpiresAtBefore(LocalDateTime expiresAt);
}
