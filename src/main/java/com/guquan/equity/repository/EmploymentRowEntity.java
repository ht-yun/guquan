package com.guquan.equity.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "employment_job_row")
public class EmploymentRowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String jobId;

    @Column(nullable = false)
    private Long companyTaskId;

    @Column(nullable = false)
    private int rowNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
