package com.guquan.equity.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompanyNameNormalizerTest {

    @Test
    void normalizesFullWidthPunctuationAndWhitespaceWithoutRemovingLegalText() {
        assertThat(CompanyNameNormalizer.normalize(" 深圳示例科技（有限）公司 "))
                .isEqualTo(CompanyNameNormalizer.normalize("深圳示例科技(有限)公司"));
        assertThat(CompanyNameNormalizer.normalize("深圳示例科技有限公司龙岗分公司"))
                .contains("龙岗分公司");
    }
}
