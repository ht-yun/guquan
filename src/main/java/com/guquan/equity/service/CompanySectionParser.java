package com.guquan.equity.service;

import com.guquan.equity.model.CompanyInfoSection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CompanySectionParser {
    private static final Pattern NO_RECORD = Pattern.compile(
            "(?:\\u6682\\u65e0|\\u65e0|\\u672a\\u67e5\\u5230|\\u672a\\u53d1\\u73b0|\\u6ca1\\u6709).{0,16}(?:\\u76f8\\u5173)?(?:\\u8bb0\\u5f55|\\u4fe1\\u606f|\\u6570\\u636e)|"
                    + "(?:\\u672a\\u5217\\u5165|\\u65e0\\u5bf9\\u5916\\u6295\\u8d44)");
    private static final Pattern CREDIT_CODE = Pattern.compile("(?<![0-9A-Z])[0-9A-Z]{18}(?![0-9A-Z])");
    private static final Pattern POSITION_TOTAL = Pattern.compile("共计\\s*(\\d+)\\s*条信息");

    public ParsedSection parse(CompanyInfoSection section, String rawText) {
        if (!StringUtils.hasText(rawText)) throw new IllegalArgumentException("请粘贴官网企业信息全文");
        String text = clean(rawText);
        if (section == CompanyInfoSection.BASIC) return parseBasic(text);
        List<Map<String, String>> records = parseTable(section, text);
        if (!records.isEmpty()) return new ParsedSection("PARSED", records);
        if (section == CompanyInfoSection.POSITIONS) {
            records = parsePositionCards(text);
            if (!records.isEmpty()) return new ParsedSection("PARSED", records);
        }
        if (NO_RECORD.matcher(text).find()) {
            return new ParsedSection("NO_RECORD", List.of());
        }
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("rawText", text);
        return new ParsedSection("PARTIAL", List.of(fallback));
    }

    private ParsedSection parseBasic(String text) {
        Map<String, String> record = new LinkedHashMap<>();
        put(record, "companyName", labeled(text, "\\u4f01\\u4e1a\\u540d\\u79f0", "\\u540d\\u79f0"));
        String code = labeled(text, "\\u7edf\\u4e00\\u793e\\u4f1a\\u4fe1\\u7528\\u4ee3\\u7801", "\\u6ce8\\u518c\\u53f7");
        if (!StringUtils.hasText(code)) { Matcher matcher = CREDIT_CODE.matcher(text.toUpperCase()); if (matcher.find()) code = matcher.group(); }
        put(record, "creditCode", code);
        put(record, "legalPerson", labeled(text, "\\u6cd5\\u5b9a\\u4ee3\\u8868\\u4eba", "\\u8d1f\\u8d23\\u4eba", "\\u7ecf\\u8425\\u8005"));
        put(record, "registrationStatus", labeled(text, "\\u767b\\u8bb0\\u72b6\\u6001", "\\u7ecf\\u8425\\u72b6\\u6001"));
        put(record, "entityType", labeled(text, "\\u4f01\\u4e1a\\u7c7b\\u578b", "\\u7c7b\\u578b"));
        put(record, "registeredCapital", labeled(text, "\\u6ce8\\u518c\\u8d44\\u672c"));
        put(record, "establishedAt", labeled(text, "\\u6210\\u7acb\\u65e5\\u671f"));
        put(record, "registrationAuthority", labeled(text, "\\u767b\\u8bb0\\u673a\\u5173"));
        put(record, "registeredAddress", labeled(text, "\\u4f4f\\u6240", "\\u6ce8\\u518c\\u5730\\u5740", "\\u7ecf\\u8425\\u573a\\u6240"));
        put(record, "businessScope", labeled(text, "\\u7ecf\\u8425\\u8303\\u56f4"));
        put(record, "industry", labeled(text, "\\u6240\\u5c5e\\u884c\\u4e1a", "\\u884c\\u4e1a\\u95e8\\u7c7b"));
        return record.isEmpty() ? new ParsedSection("PARTIAL", List.of(Map.of("rawText", text)))
                : new ParsedSection("PARSED", List.of(record));
    }

    private List<Map<String, String>> parseTable(CompanyInfoSection section, String text) {
        List<String> lines = lines(text).stream().filter(line -> !isHeading(line)).toList();
        Set<String> knownHeaders = headers(section);
        for (int i = 0; i < lines.size(); i++) {
            List<String> cells = splitCells(lines.get(i));
            if (cells.size() >= 2 && cells.stream().anyMatch(cell -> isKnownHeader(cell, knownHeaders))) {
                List<Map<String, String>> rows = rowsFromDelimited(lines, i, cells);
                if (!rows.isEmpty()) return rows;
            }
        }
        return rowsFromVerticalCells(lines, knownHeaders);
    }

    private List<Map<String, String>> rowsFromDelimited(List<String> lines, int headerIndex, List<String> headers) {
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            List<String> values = splitCells(lines.get(i));
            if (values.size() < 2 || isHeaderRow(values, headers)) continue;
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) row.put(headers.get(c), c < values.size() ? values.get(c) : "");
            result.add(row);
        }
        return result;
    }

    private List<Map<String, String>> rowsFromVerticalCells(List<String> lines, Set<String> knownHeaders) {
        List<String> headers = new ArrayList<>();
        int firstValue = -1;
        for (int i = 0; i < lines.size(); i++) {
            String value = stripPunctuation(lines.get(i));
            if (isKnownHeader(value, knownHeaders)) headers.add(value);
            else if (!headers.isEmpty()) { firstValue = i; break; }
        }
        if (headers.size() < 2 || firstValue < 0) return List.of();
        List<String> values = lines.subList(firstValue, lines.size()).stream()
                .filter(value -> !looksLikeHeader(value) && !isNoise(value)).toList();
        List<Map<String, String>> result = new ArrayList<>();
        for (int offset = 0; offset + headers.size() <= values.size(); offset += headers.size()) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) row.put(headers.get(i), values.get(offset + i));
            result.add(row);
        }
        return result;
    }

    /**
     * The GSXT "主要人员" module is frequently copied as a list of name cards,
     * not a table. Some provincial pages also interleave base64-looking text
     * into the visible name. Retain only the visible Chinese characters and
     * never manufacture a missing position/title.
     */
    private List<Map<String, String>> parsePositionCards(String text) {
        List<String> lines = lines(text);
        for (int index = 0; index < lines.size(); index++) {
            Matcher total = POSITION_TOTAL.matcher(lines.get(index));
            if (!total.find()) continue;
            int expected = Integer.parseInt(total.group(1));
            List<Map<String, String>> result = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int cursor = index + 1; cursor < lines.size() && result.size() < expected; cursor++) {
                String line = lines.get(cursor);
                if (isHeading(line) || isNoise(line)) continue;
                String name = cleanPersonName(line);
                if (!StringUtils.hasText(name) || name.length() > 20 || !seen.add(name)) continue;
                result.add(Map.of("姓名", name));
            }
            if (!result.isEmpty()) return result;
        }
        return List.of();
    }

    private String cleanPersonName(String value) {
        String result = value.replaceAll("[A-Za-z0-9+/=]+", "")
                .replaceAll("[^\\p{IsHan}·]", "");
        for (int length = 1; length * 2 <= result.length(); length++) {
            String prefix = result.substring(0, length);
            if (result.startsWith(prefix + prefix)) {
                result = prefix + result.substring(length * 2);
                break;
            }
        }
        return result;
    }

    private String labeled(String text, String... labels) {
        List<String> lines = lines(text);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String label : labels) {
                String actual = unicode(label);
                int position = line.indexOf(actual);
                if (position < 0) continue;
                String value = line.substring(position + actual.length()).replaceFirst("^[\\s\\t:：|]+", "").trim();
                if (StringUtils.hasText(value) && !looksLikeHeader(value)) return value;
                if (i + 1 < lines.size() && !looksLikeHeader(lines.get(i + 1))) return lines.get(i + 1).trim();
            }
        }
        return null;
    }

    private List<String> lines(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\R")) if (StringUtils.hasText(line)) result.add(line.trim());
        return result;
    }

    private List<String> splitCells(String line) {
        String[] parts = line.split("\\t+|\\s{2,}|\\s*[|｜]\\s*");
        List<String> result = new ArrayList<>();
        for (String part : parts) if (StringUtils.hasText(part)) result.add(part.trim());
        return result;
    }

    private Set<String> headers(CompanyInfoSection section) {
        String value = switch (section) {
            case SHAREHOLDERS -> "股东,股东名称,发起人,认缴出资额,认缴出资日期,认缴出资方式,实缴出资额,实缴出资日期,实缴出资方式";
            case INVESTMENTS -> "被投资企业名称,企业名称,统一社会信用代码,投资比例,投资金额,法定代表人,登记状态";
            case POSITIONS -> "姓名,人员姓名,职务,任职企业,任职状态";
            case ABNORMAL -> "列入经营异常名录原因,列入日期,作出决定机关,移出经营异常名录原因,移出日期";
            case SERIOUS_VIOLATIONS -> "列入严重违法失信名单原因,列入日期,作出决定机关,移出严重违法失信名单原因,移出日期";
            default -> "";
        };
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split(",")) if (!item.isBlank()) result.add(item);
        return result;
    }

    private boolean isKnownHeader(String value, Set<String> headers) {
        String normalized = stripPunctuation(value);
        return headers.stream().anyMatch(header -> normalized.equals(header) || normalized.contains(header));
    }
    private boolean isHeaderRow(List<String> values, List<String> headers) {
        if (values.stream().allMatch(this::looksLikeHeader)) return true;
        Set<String> known = new LinkedHashSet<>(headers);
        long matches = values.stream().filter(value -> isKnownHeader(value, known)).count();
        return matches >= Math.max(2, (int) Math.ceil(values.size() * 0.6));
    }
    private boolean looksLikeHeader(String value) { return value.length() <= 30 && (value.endsWith("信息") || value.endsWith("名称") || value.endsWith("状态") || value.endsWith("日期") || value.endsWith("机关") || value.endsWith("原因") || value.contains("出资方式") || value.contains("出资金额") || value.contains("出资额") || value.contains("出资日期") || value.equals("公示日期") || value.equals("股东") || value.equals("姓名") || value.equals("职务")); }
    private boolean isHeading(String value) {
        String compact = stripPunctuation(value);
        return compact.matches("^(企业基本信息|登记信息|基本信息|股东及出资信息?|股东信息|"
                + "对外投资信息?|主要人员信息?|主要成员|任职信息|经营异常名录信息?|经营异常信息|"
                + "严重违法失信名单信息?|严重违法失信信息)$");
    }
    private boolean isNoise(String value) { return value.matches("^(序号|查看|详情|展开|收起|共\\d+条|共计\\s*\\d+条|首页上一页.*).*$"); }
    private String stripPunctuation(String value) { return value.replaceAll("[\\s:：|｜（）()]+", "").trim(); }
    private String clean(String value) { return value.replace('\u00a0', ' ').replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim(); }
    private void put(Map<String, String> target, String key, String value) { if (StringUtils.hasText(value)) target.put(key, value.trim()); }
    private String unicode(String escaped) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < escaped.length();) {
            if (i + 6 <= escaped.length() && escaped.charAt(i) == '\\' && escaped.charAt(i + 1) == 'u') {
                result.append((char) Integer.parseInt(escaped.substring(i + 2, i + 6), 16));
                i += 6;
            } else {
                result.append(escaped.charAt(i++));
            }
        }
        return result.toString();
    }

    public record ParsedSection(String status, List<Map<String, String>> records) {}
}
