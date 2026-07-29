package com.guquan.equity.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "company_batch_job")
public class CompanyBatchJobEntity {
    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
