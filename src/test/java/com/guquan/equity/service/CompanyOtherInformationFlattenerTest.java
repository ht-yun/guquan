package com.guquan.equity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guquan.equity.model.CompanyInfoSection;
import com.guquan.equity.repository.CompanyBatchCompanyEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompanyOtherInformationFlattenerTest {

    private final CompanyOtherInformationFlattener flattener = new CompanyOtherInformationFlattener();

    @Test
    void movesOnlyNonMainBasicFieldsToTheOtherInformationList() {
        CompanyBatchCompanyEntity company = company();
        var result = flattener.flatten(company, CompanyInfoSection.BASIC, "PARSED", List.of(Map.of(
                "companyName", "示例公司", "creditCode", "91310000MA12345678",
                "registeredCapital", "100万元", "establishedAt", "2020年1月1日",
                "businessScope", "软件开发")));

        assertThat(result).extracting(item -> item.getFieldName()).containsExactlyInAnyOrder("注册资本", "成立日期", "经营范围");
        assertThat(result).extracting(item -> item.getValue()).containsExactlyInAnyOrder("100万元", "2020年1月1日", "软件开发");
    }

    @Test
    void keepsOneToManyShareholderFieldsAsSeparateRows() {
        var result = flattener.flatten(company(), CompanyInfoSection.SHAREHOLDERS, "PARSED", List.of(
                Map.of("股东名称", "甲公司", "认缴出资额", "100万元"),
                Map.of("股东名称", "乙公司", "认缴出资额", "50万元")));

        assertThat(result).hasSize(4);
        assertThat(result.get(0).getRecordNumber()).isEqualTo(1);
        assertThat(result.get(2).getRecordNumber()).isEqualTo(2);
    }

    @Test
    void emitsAnExplicitStatusWhenThereAreNoRecords() {
        var result = flattener.flatten(company(), CompanyInfoSection.ABNORMAL, "NO_RECORD", List.of());

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getFieldName()).isEqualTo("栏目状态");
            assertThat(item.getValue()).isEqualTo("官网明确显示无记录");
        });
    }

    private CompanyBatchCompanyEntity company() {
        CompanyBatchCompanyEntity company = new CompanyBatchCompanyEntity();
        company.setId(1L);
        company.setInputCompanyName("示例公司");
        company.setOfficialCompanyName("示例公司");
        company.setCreditCode("91310000MA12345678");
        return company;
    }
}
