package com.guquan.equity.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CompanyNameNormalizer {

    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private CompanyNameNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        normalized = INVISIBLE.matcher(normalized).replaceAll("");
        normalized = WHITESPACE.matcher(normalized).replaceAll("");
        return normalized
                .replace('（', '(')
                .replace('）', ')')
                .replace('，', ',')
                .replace('：', ':')
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
