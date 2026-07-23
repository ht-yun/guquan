package com.guquan.equity.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "employment_job")
public class EmploymentJobEntity {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 1000)
    private String inputPath;

    @Column(nullable = false, length = 30)
    private String status;

    private int totalRecords;
    private int uniqueCompanies;

    @Lob
    private String validationErrorsJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
