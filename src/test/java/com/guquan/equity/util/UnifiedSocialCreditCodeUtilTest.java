package com.guquan.equity.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnifiedSocialCreditCodeUtilTest {

    @Test
    void validatesKnownCreditCodeAndSeparatesRegistrationAuthorityCode() {
        var result = UnifiedSocialCreditCodeUtil.parse("914403001922038216");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getRegistrationAuthorityCode()).isEqualTo("440300");
    }

    @Test
    void rejectsInvalidCheckCode() {
        assertThat(UnifiedSocialCreditCodeUtil.parse("914403001922038210").isValid()).isFalse();
    }
}
