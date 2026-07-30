package com.guquan.equity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guquan.equity.model.CompanyProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyBrowserCandidateResolverTest {

    private final CompanyBrowserCandidateResolver resolver = new CompanyBrowserCandidateResolver();

    @Test
    void selectsTheOnlyExactNormalizedName() {
        CompanyProfile exact = CompanyProfile.builder().companyName("北京示例科技有限公司")
                .creditCode("91110108100000000D").build();
        CompanyProfile other = CompanyProfile.builder().companyName("北京示例科技服务有限公司")
                .creditCode("91110108100000001B").build();

        var result = resolver.resolve("北京 示例科技有限公司", null, List.of(exact, other));

        assertThat(result.automaticCandidate()).isEqualTo(exact);
        assertThat(result.requiresSelection()).isFalse();
    }

    @Test
    void requiresHumanChoiceForSameNameWithDifferentCreditCodes() {
        CompanyProfile first = CompanyProfile.builder().companyName("示例有限公司")
                .creditCode("91110108100000000D").build();
        CompanyProfile second = CompanyProfile.builder().companyName("示例有限公司")
                .creditCode("91110108100000001B").build();

        var result = resolver.resolve("示例有限公司", null, List.of(first, second));

        assertThat(result.automaticCandidate()).isNull();
        assertThat(result.requiresSelection()).isTrue();
        assertThat(result.candidates()).containsExactly(first, second);
    }

    @Test
    void creditCodeTakesPriorityOverName() {
        CompanyProfile first = CompanyProfile.builder().companyName("示例有限公司")
                .creditCode("91110108100000000D").build();
        CompanyProfile second = CompanyProfile.builder().companyName("示例有限公司")
                .creditCode("91110108100000001B").build();

        var result = resolver.resolve("示例有限公司", "91110108100000001B", List.of(first, second));

        assertThat(result.automaticCandidate()).isEqualTo(second);
    }

    @Test
    void requiresHumanChoiceWhenTheOnlyResultHasADifferentName() {
        CompanyProfile fuzzy = CompanyProfile.builder().companyName("北京示例科技服务有限公司")
                .creditCode("91110108100000001B").build();

        var result = resolver.resolve("北京示例科技有限公司", null, List.of(fuzzy));

        assertThat(result.automaticCandidate()).isNull();
        assertThat(result.requiresSelection()).isTrue();
    }
}
