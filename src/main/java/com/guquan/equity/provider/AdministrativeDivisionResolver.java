package com.guquan.equity.provider;

import java.util.Map;

final class AdministrativeDivisionResolver {

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("北京市", "110000"),
            Map.entry("天津市", "120000"),
            Map.entry("河北省", "130000"),
            Map.entry("山西省", "140000"),
            Map.entry("内蒙古自治区", "150000"),
            Map.entry("辽宁省", "210000"),
            Map.entry("吉林省", "220000"),
            Map.entry("黑龙江省", "230000"),
            Map.entry("上海市", "310000"),
            Map.entry("江苏省", "320000"),
            Map.entry("浙江省", "330000"),
            Map.entry("安徽省", "340000"),
            Map.entry("福建省", "350000"),
            Map.entry("江西省", "360000"),
            Map.entry("山东省", "370000"),
            Map.entry("河南省", "410000"),
            Map.entry("湖北省", "420000"),
            Map.entry("湖南省", "430000"),
            Map.entry("广东省", "440000"),
            Map.entry("广西壮族自治区", "450000"),
            Map.entry("海南省", "460000"),
            Map.entry("重庆市", "500000"),
            Map.entry("四川省", "510000"),
            Map.entry("贵州省", "520000"),
            Map.entry("云南省", "530000"),
            Map.entry("西藏自治区", "540000"),
            Map.entry("陕西省", "610000"),
            Map.entry("甘肃省", "620000"),
            Map.entry("青海省", "630000"),
            Map.entry("宁夏回族自治区", "640000"),
            Map.entry("新疆维吾尔自治区", "650000"),
            Map.entry("海淀区", "110108"),
            Map.entry("浦东新区", "310115")
    );

    private AdministrativeDivisionResolver() {
    }

    static String codeOf(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        String exact = CODES.get(normalized);
        if (exact != null) {
            return exact;
        }
        return CODES.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey()) || entry.getKey().contains(normalized))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
