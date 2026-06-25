package com.guquan.equity.provider;

import com.guquan.equity.api.EquityDataProvider;
import com.guquan.equity.model.CompanyCandidate;
import com.guquan.equity.model.ShareholderHolding;
import com.guquan.equity.model.PersonHolding;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MockEquityDataProvider implements EquityDataProvider {

    private static final String SOURCE = "MOCK";

    private final List<CompanyCandidate> companies = List.of(
            CompanyCandidate.builder()
                    .companyName("北京示例科技有限公司")
                    .creditCode("911101000000000001")
                    .legalPerson("李四")
                    .status("存续")
                    .listedCompany(false)
                    .source(SOURCE)
                    .build(),
            CompanyCandidate.builder()
                    .companyName("上海示例科技股份有限公司")
                    .creditCode("913101000000000002")
                    .legalPerson("王五")
                    .status("存续")
                    .listedCompany(true)
                    .stockCode("600000.SH")
                    .source(SOURCE)
                    .build(),
            CompanyCandidate.builder()
                    .companyName("示例科技有限公司")
                    .creditCode("914401000000000003")
                    .legalPerson("赵六")
                    .status("存续")
                    .listedCompany(false)
                    .source(SOURCE)
                    .build()
    );

    @Override
    public String providerName() {
        return SOURCE;
    }

    @Override
    public List<CompanyCandidate> searchCompany(String companyName) {
        if (!StringUtils.hasText(companyName)) {
            return List.of();
        }
        String keyword = companyName.trim();
        return companies.stream()
                .filter(company -> company.getCompanyName().contains(keyword)
                        || keyword.contains(company.getCompanyName()))
                .toList();
    }

    @Override
    public Optional<CompanyCandidate> getCompanyByCreditCode(String creditCode) {
        if (!StringUtils.hasText(creditCode)) {
            return Optional.empty();
        }
        String normalized = creditCode.trim();
        return companies.stream()
                .filter(company -> normalized.equalsIgnoreCase(company.getCreditCode()))
                .findFirst();
    }

    @Override
    public List<ShareholderHolding> getIndustrialRegistryHoldings(CompanyCandidate company) {
        if ("911101000000000001".equals(company.getCreditCode())) {
            return List.of(
                    ShareholderHolding.builder()
                            .shareholderName("张三")
                            .shareholderType("自然人股东")
                            .subscribedAmount(new BigDecimal("100.0000"))
                            .subscribedRatio(new BigDecimal("0.200000"))
                            .paidInAmount(new BigDecimal("60.0000"))
                            .paidInRatio(new BigDecimal("0.150000"))
                            .currency("人民币万元")
                            .source(SOURCE)
                            .sourceUpdatedAt(LocalDate.of(2026, 6, 16))
                            .build(),
                    ShareholderHolding.builder()
                            .shareholderName("李四")
                            .shareholderType("自然人股东")
                            .subscribedAmount(new BigDecimal("400.0000"))
                            .subscribedRatio(new BigDecimal("0.800000"))
                            .paidInAmount(new BigDecimal("340.0000"))
                            .paidInRatio(new BigDecimal("0.850000"))
                            .currency("人民币万元")
                            .source(SOURCE)
                            .sourceUpdatedAt(LocalDate.of(2026, 6, 16))
                            .build()
            );
        }
        if ("913101000000000002".equals(company.getCreditCode())) {
            return List.of(
                    ShareholderHolding.builder()
                            .shareholderName("张三")
                            .shareholderType("自然人股东")
                            .subscribedAmount(new BigDecimal("50.0000"))
                            .subscribedRatio(new BigDecimal("0.010000"))
                            .paidInAmount(new BigDecimal("50.0000"))
                            .paidInRatio(new BigDecimal("0.010000"))
                            .currency("人民币万元")
                            .source(SOURCE)
                            .sourceUpdatedAt(LocalDate.of(2026, 6, 16))
                            .build()
            );
        }
        return List.of(
                ShareholderHolding.builder()
                        .shareholderName("张三")
                        .shareholderType("自然人股东")
                        .subscribedAmount(new BigDecimal("30.0000"))
                        .subscribedRatio(new BigDecimal("0.300000"))
                        .paidInAmount(BigDecimal.ZERO)
                        .paidInRatio(BigDecimal.ZERO)
                        .currency("人民币万元")
                        .source(SOURCE)
                        .sourceUpdatedAt(LocalDate.of(2026, 6, 16))
                        .build()
        );
    }

    @Override
    public List<ShareholderHolding> getListedCompanyHoldings(CompanyCandidate company) {
        if (!Boolean.TRUE.equals(company.getListedCompany())) {
            return List.of();
        }
        return List.of(
                ShareholderHolding.builder()
                        .shareholderName("张三")
                        .shareholderType("自然人股东")
                        .listedHoldingAmount(new BigDecimal("120000.0000"))
                        .listedHoldingRatio(new BigDecimal("0.005000"))
                        .currency("股")
                        .source(SOURCE)
                        .sourceUpdatedAt(LocalDate.of(2026, 6, 16))
                        .build()
        );
    }

    @Override
    public List<PersonHolding> searchPersonHoldings(String personName) {
        if (!StringUtils.hasText(personName)) {
            return List.of();
        }
        String name = personName.trim();
        if (!"张三".equals(name)) {
            return List.of();
        }
        return List.of(
                PersonHolding.builder()
                        .companyName("上海示例科技股份有限公司")
                        .creditCode("600000.SH")
                        .shareholderName("张三")
                        .shareholderType("十大股东")
                        .holdingAmount(new BigDecimal("120000.0000"))
                        .holdingRatio(new BigDecimal("0.005000"))
                        .currency("股")
                        .dataSource(SOURCE)
                        .sourceUpdatedAt(LocalDate.of(2026, 6, 16))
                        .build(),
                PersonHolding.builder()
                        .companyName("北京示例科技有限公司")
                        .creditCode("911101000000000001")
                        .shareholderName("张三")
                        .shareholderType("十大股东")
                        .holdingAmount(new BigDecimal("100000.0000"))
                        .holdingRatio(new BigDecimal("0.100000"))
                        .currency("股")
                        .dataSource(SOURCE)
                        .sourceUpdatedAt(LocalDate.of(2026, 6, 16))
                        .build()
        );
    }
}
