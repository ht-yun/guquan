package com.guquan.equity.service;

import com.guquan.equity.model.CompanyProfile;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmploymentStandardizationService {

    private static final Map<String, List<String>> INDUSTRY_RULES = industryRules();

    public Suggestion suggest(
            CompanyProfile profile,
            EmploymentWorkbookService.ReferenceData references) {
        String industry = exactOrMapped(
                profile.getEmploymentIndustry(),
                profile.getIndustryName(),
                references.industries(),
                INDUSTRY_RULES);
        String nature = exactNatureOrMapped(profile, references.unitNatures());
        AreaMatch area = resolveArea(profile, references);
        boolean complete = StringUtils.hasText(profile.getCreditCode())
                && StringUtils.hasText(industry)
                && StringUtils.hasText(nature)
                && area != null;
        String source = StringUtils.hasText(profile.getEmploymentIndustry())
                || StringUtils.hasText(profile.getEmploymentUnitNature())
                ? "EXISTING_STANDARD" : "AUTO_RULE";
        return new Suggestion(
                industry,
                nature,
                area == null ? null : area.code(),
                area == null ? null : area.name(),
                source,
                complete ? "HIGH" : "REVIEW");
    }

    private String exactOrMapped(
            String standardValue,
            String rawValue,
            Set<String> allowed,
            Map<String, List<String>> rules) {
        String exact = exact(standardValue, allowed);
        if (exact != null) {
            return exact;
        }
        exact = exact(rawValue, allowed);
        if (exact != null) {
            return exact;
        }
        String normalized = normalize(rawValue);
        if (normalized.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> rule : rules.entrySet()) {
            if (!allowed.contains(rule.getKey())) {
                continue;
            }
            if (rule.getValue().stream().map(this::normalize).anyMatch(normalized::contains)) {
                return rule.getKey();
            }
        }
        return null;
    }

    private String exactNatureOrMapped(CompanyProfile profile, Set<String> allowed) {
        String exact = exact(profile.getEmploymentUnitNature(), allowed);
        if (exact != null) {
            return exact;
        }
        exact = exact(profile.getEntityType(), allowed);
        if (exact != null) {
            return exact;
        }
        String raw = normalize(profile.getEntityType());
        if (raw.isEmpty()) {
            return null;
        }
        if (containsAny(raw, "个体工商户") && allowed.contains("个体工商户")) {
            return "个体工商户";
        }
        if (containsAny(raw, "外商投资", "外国法人独资", "台港澳", "中外合资", "中外合作")
                && allowed.contains("外商投资企业")) {
            return "外商投资企业";
        }
        if (containsAny(raw, "国有独资", "全民所有制", "国有企业") && allowed.contains("国有企业")) {
            return "国有企业";
        }
        if (containsAny(raw, "机关法人", "党政机关") && allowed.contains("机关")) {
            return "机关";
        }
        if (containsAny(raw, "社会团体法人", "民办非企业", "基金会法人", "社会组织")
                && allowed.contains("其他（含社会组织、国际组织等）")) {
            return "其他（含社会组织、国际组织等）";
        }
        if (containsAny(raw, "非自然人投资或控股", "法人独资", "国有控股")) {
            return null;
        }
        if (containsAny(raw, "自然人投资或控股", "自然人独资", "个人独资企业", "普通合伙", "有限合伙", "私营")
                && allowed.contains("其他企业（含民营企业等）")) {
            return "其他企业（含民营企业等）";
        }
        return null;
    }

    private AreaMatch resolveArea(
            CompanyProfile profile,
            EmploymentWorkbookService.ReferenceData references) {
        String explicitCode = firstText(profile.getRegisteredAddressAreaCode(), profile.getAreaCode());
        if (StringUtils.hasText(explicitCode)) {
            String name = references.areaNames().get(explicitCode.trim());
            if (name != null) {
                return new AreaMatch(explicitCode.trim(), name);
            }
        }
        String address = normalize(profile.getRegisteredAddress());
        if (address.isEmpty()) {
            return null;
        }
        List<Map.Entry<String, String>> matches = references.areasByNameLength().stream()
                .filter(entry -> address.contains(normalize(entry.getValue())))
                .toList();
        AreaMatch full = uniqueLongest(matches);
        if (full != null) {
            return full;
        }

        Map<String, List<Map.Entry<String, String>>> tailMatches = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : references.areaNames().entrySet()) {
            String tail = areaTail(entry.getValue());
            if (tail.length() >= 3 && address.contains(normalize(tail))) {
                tailMatches.computeIfAbsent(tail, ignored -> new ArrayList<>()).add(entry);
            }
        }
        return tailMatches.entrySet().stream()
                .filter(entry -> entry.getValue().size() == 1)
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .map(entry -> new AreaMatch(entry.getValue().get(0).getKey(), entry.getValue().get(0).getValue()))
                .findFirst()
                .orElse(null);
    }

    private AreaMatch uniqueLongest(List<Map.Entry<String, String>> matches) {
        if (matches.isEmpty()) {
            return null;
        }
        int longest = matches.get(0).getValue().length();
        List<Map.Entry<String, String>> best = matches.stream()
                .filter(entry -> entry.getValue().length() == longest)
                .toList();
        return best.size() == 1 ? new AreaMatch(best.get(0).getKey(), best.get(0).getValue()) : null;
    }

    private String areaTail(String displayName) {
        int cut = Math.max(
                Math.max(displayName.lastIndexOf('市'), displayName.lastIndexOf('州')),
                displayName.lastIndexOf('盟'));
        return cut >= 0 && cut + 1 < displayName.length()
                ? displayName.substring(cut + 1) : displayName;
    }

    private String exact(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return allowed.contains(trimmed) ? trimmed : null;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static Map<String, List<String>> industryRules() {
        Map<String, List<String>> rules = new LinkedHashMap<>();
        rules.put("农、林、牧、渔业", List.of("农业", "林业", "畜牧业", "渔业", "农林牧渔"));
        rules.put("采矿业", List.of("采矿", "煤炭开采", "石油和天然气开采", "有色金属矿采选"));
        rules.put("制造业", List.of("制造业", "制造", "加工制造"));
        rules.put("电力、热力、燃气及水生产和供应业", List.of("电力", "热力", "燃气", "水生产和供应"));
        rules.put("建筑业", List.of("建筑业", "房屋建筑", "土木工程", "建筑安装", "建筑装饰"));
        rules.put("批发和零售业", List.of("批发业", "零售业", "批发和零售"));
        rules.put("交通运输、仓储和邮政业", List.of("道路运输", "铁路运输", "航空运输", "水上运输", "仓储", "邮政", "多式联运", "交通运输"));
        rules.put("住宿和餐饮业", List.of("住宿业", "餐饮业", "住宿和餐饮"));
        rules.put("信息传输、软件和信息技术服务业", List.of("互联网", "软件", "信息技术服务", "电信", "广播电视传输", "信息传输"));
        rules.put("金融业", List.of("金融业", "银行", "保险", "证券", "资本市场"));
        rules.put("房地产业", List.of("房地产业", "房地产"));
        rules.put("租赁和商务服务业", List.of("租赁业", "商务服务", "租赁和商务"));
        rules.put("科学研究和技术服务业", List.of("研究和试验发展", "专业技术服务", "科技推广", "科学研究", "技术服务"));
        rules.put("水利、环境和公共设施管理业", List.of("水利管理", "生态保护", "环境治理", "公共设施管理"));
        rules.put("居民服务、修理和其他服务业", List.of("居民服务", "机动车维修", "修理业", "其他服务业"));
        rules.put("教育", List.of("教育"));
        rules.put("卫生和社会工作", List.of("卫生", "社会工作"));
        rules.put("文化、体育和娱乐业", List.of("新闻和出版", "广播电视电影", "文化艺术", "体育", "娱乐业"));
        rules.put("公共管理、社会保障和社会组织", List.of("国家机构", "社会保障", "社会组织", "公共管理"));
        rules.put("国际组织", List.of("国际组织"));
        rules.put("军队", List.of("军队"));
        return rules;
    }

    public record Suggestion(
            String industry,
            String unitNature,
            String areaCode,
            String areaName,
            String source,
            String confidence) {
    }

    private record AreaMatch(String code, String name) {
    }
}
