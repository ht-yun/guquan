package com.guquan.equity.service;

import com.guquan.equity.model.CompanyBrowserTaskRequest;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.util.UnifiedSocialCreditCodeUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GsxtPageParser {

    private static final Pattern CREDIT_CODE = Pattern.compile("[0-9A-Z]{18}");
    private static final Pattern AREA_CODE = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    public List<CompanyProfile> parse(String body, CompanyBrowserTaskRequest request) {
        return parse(body, request, "GSXT_BROWSER");
    }

    public List<CompanyProfile> parse(
            String body, CompanyBrowserTaskRequest request, String source) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        String[] lines = body.split("\\R");
        List<CodePosition> positions = findCreditCodes(lines);
        List<CompanyProfile> profiles = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            CodePosition current = positions.get(i);
            int previousBlockEnd = i == 0 ? 0 : positions.get(i - 1).lineIndex + 1;
            int start = Math.max(previousBlockEnd, current.lineIndex - 1);
            int nameSearchStart = previousBlockEnd;
            int end = i == positions.size() - 1
                    ? lines.length : Math.max(current.lineIndex + 1, positions.get(i + 1).lineIndex - 1);
            String segment = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
            String addressCode = extractAreaCode(segment);
            profiles.add(CompanyProfile.builder()
                    .companyName(extractCompanyName(lines, nameSearchStart, current, request))
                    .creditCode(current.code)
                    .legalPerson(extractLabeledValue(segment, "法定代表人", "负责人", "经营者"))
                    .registrationStatus(extractLabeledValue(segment, "登记状态", "经营状态"))
                    .industryName(extractLabeledValue(segment, "所属行业", "行业门类", "行业"))
                    .entityType(extractLabeledValue(segment, "企业类型", "登记注册类型", "登记类型", "类型"))
                    .registrationAuthority(extractLabeledValue(segment, "登记机关"))
                    .registeredAddress(extractLabeledValue(segment, "住所", "注册地址", "经营场所"))
                    .registrationAuthorityCode(current.code.substring(2, 8))
                    .registeredAddressAreaCode(addressCode)
                    .areaCode(addressCode)
                    .areaCodeSource(addressCode == null ? "UNKNOWN" : "GSXT_EXPLICIT")
                    .source(StringUtils.hasText(source) ? source.trim() : "GSXT_BROWSER")
                    .sourceUpdatedAt(LocalDate.now())
                    .build());
        }
        return profiles;
    }

    private List<CodePosition> findCreditCodes(String[] lines) {
        List<CodePosition> result = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            Matcher matcher = CREDIT_CODE.matcher(lines[lineIndex].toUpperCase(Locale.ROOT));
            while (matcher.find()) {
                String code = matcher.group();
                if (UnifiedSocialCreditCodeUtil.parse(code).isValid()
                        && result.stream().noneMatch(item -> item.code.equals(code))) {
                    result.add(new CodePosition(code, lineIndex));
                }
            }
        }
        return result;
    }

    private String extractCompanyName(
            String[] lines, int segmentStart, CodePosition current, CompanyBrowserTaskRequest request) {
        String sameLine = cleanName(lines[current.lineIndex], current.code);
        if (isLikelyCompanyName(sameLine)) {
            return sameLine;
        }
        for (int i = current.lineIndex - 1; i >= segmentStart; i--) {
            String candidate = lines[i].trim();
            if (isLikelyCompanyName(candidate)) {
                return candidate;
            }
        }
        return request != null && StringUtils.hasText(request.getCompanyName())
                ? request.getCompanyName().trim() : null;
    }

    private String cleanName(String line, String code) {
        return line.replace(code, "")
                .replace("统一社会信用代码", "")
                .replaceFirst("^[：:\\s]+", "")
                .trim();
    }

    private boolean isLikelyCompanyName(String value) {
        if (!StringUtils.hasText(value) || value.length() > 255) {
            return false;
        }
        if (looksLikeLabel(value) || CREDIT_CODE.matcher(value.toUpperCase(Locale.ROOT)).find()) {
            return false;
        }
        return !value.matches(".*(法定代表人|负责人|经营者|登记状态|所属行业|企业类型|登记机关|住所|地址).*[:：].*");
    }

    private String extractAreaCode(String segment) {
        String explicit = extractLabeledValue(segment,
                "住所所在地行政区划代码", "注册地址行政区划代码", "行政区划代码", "所在地代码");
        if (!StringUtils.hasText(explicit)) {
            return null;
        }
        Matcher matcher = AREA_CODE.matcher(explicit);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractLabeledValue(String body, String... labels) {
        String[] lines = body.split("\\R");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String normalized = lines[lineIndex].trim();
            for (String label : labels) {
                int index = normalized.indexOf(label);
                if (index < 0) {
                    continue;
                }
                String value = normalized.substring(index + label.length())
                        .replaceFirst("^[：:\\s]+", "").trim();
                if (StringUtils.hasText(value) && value.length() <= 500) {
                    return value;
                }
                if (!StringUtils.hasText(value)) {
                    String following = nextNonBlankLine(lines, lineIndex + 1);
                    if (StringUtils.hasText(following) && following.length() <= 500
                            && !looksLikeLabel(following)) {
                        return following;
                    }
                }
            }
        }
        return null;
    }

    private String nextNonBlankLine(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            String value = lines[i].trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean looksLikeLabel(String value) {
        return value.matches(".{0,30}(统一社会信用代码|法定代表人|负责人|经营者|登记状态|经营状态|"
                + "所属行业|行业门类|企业类型|登记注册类型|登记类型|登记机关|住所|注册地址|经营场所|"
                + "行政区划代码|所在地代码)[：:]?.*");
    }

    private record CodePosition(String code, int lineIndex) {
    }
}
