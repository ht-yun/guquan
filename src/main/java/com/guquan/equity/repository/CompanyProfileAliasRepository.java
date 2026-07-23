package com.guquan.equity.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyProfileAliasRepository extends JpaRepository<CompanyProfileAliasEntity, Long> {

    Optional<CompanyProfileAliasEntity> findByNormalizedAlias(String normalizedAlias);
}
