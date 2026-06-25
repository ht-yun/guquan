package com.guquan.equity.repository;

import com.guquan.equity.model.QueryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "equity_query_record")
public class EquityQueryRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String queryId;

    @Column(nullable = false, length = 100)
    private String personName;

    @Column(length = 30)
    private String phoneNumber;

    @Column(length = 30)
    private String idNumber;

    @Column(length = 255)
    private String companyName;

    @Column(length = 64)
    private String creditCode;

    @Column(length = 255)
    private String matchedCompanyName;

    @Column(length = 64)
    private String matchedCreditCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private QueryStatus status;

    @Column(length = 50)
    private String confidence;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(length = 100)
    private String dataSource;

    @Column(length = 64)
    private String operatorId;

    @Column(length = 255)
    private String queryReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
