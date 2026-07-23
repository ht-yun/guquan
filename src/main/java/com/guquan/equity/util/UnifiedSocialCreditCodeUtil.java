package com.guquan.equity.util;

import com.guquan.equity.model.CreditCodeParseResult;
import java.util.HashMap;
import java.util.Map;

public final class UnifiedSocialCreditCodeUtil {

    private static final String BASE_CODE = "0123456789ABCDEFGHJKLMNPQRTUWXY";
    private static final int[] WEIGHTS = {
            1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28
    };
    private static final Map<Character, Integer> CHAR_VALUES = new HashMap<>();

    static {
        for (int i = 0; i < BASE_CODE.length(); i++) {
            CHAR_VALUES.put(BASE_CODE.charAt(i), i);
        }
    }

    private UnifiedSocialCreditCodeUtil() {
    }

    public static CreditCodeParseResult parse(String value) {
        if (value == null || value.isBlank()) {
            return invalid(value, "统一社会信用代码为空");
        }
        String code = value.trim().toUpperCase();
        if (code.length() != 18) {
            return invalid(code, "统一社会信用代码必须为18位");
        }
        for (int i = 0; i < code.length(); i++) {
            if (!CHAR_VALUES.containsKey(code.charAt(i))) {
                return invalid(code, "统一社会信用代码包含非法字符");
            }
        }
        char expected = calculateCheckCode(code.substring(0, 17));
        char actual = code.charAt(17);
        boolean valid = expected == actual;
        return CreditCodeParseResult.builder()
                .creditCode(code)
                .valid(valid)
                .message(valid ? "统一社会信用代码校验通过" : "统一社会信用代码校验位不匹配，应为 " + expected)
                .organizationTypeCode(code.substring(0, 1))
                .registrationAuthorityCode(code.substring(2, 8))
                .organizationCode(code.substring(8, 17))
                .checkCode(code.substring(17))
                .build();
    }

    public static String registrationAuthorityCode(String value) {
        CreditCodeParseResult result = parse(value);
        return result.isValid() ? result.getRegistrationAuthorityCode() : null;
    }

    private static char calculateCheckCode(String first17) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += CHAR_VALUES.get(first17.charAt(i)) * WEIGHTS[i];
        }
        int index = 31 - sum % 31;
        if (index == 31) {
            index = 0;
        }
        return BASE_CODE.charAt(index);
    }

    private static CreditCodeParseResult invalid(String code, String message) {
        return CreditCodeParseResult.builder()
                .creditCode(code)
                .valid(false)
                .message(message)
                .build();
    }
}
