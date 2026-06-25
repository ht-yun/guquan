package com.guquan.equity.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityQueryRecordRepository extends JpaRepository<EquityQueryRecordEntity, Long> {

    Optional<EquityQueryRecordEntity> findByQueryId(String queryId);
}
