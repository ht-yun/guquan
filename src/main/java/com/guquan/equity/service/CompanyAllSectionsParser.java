package com.guquan.equity.service;

import com.guquan.equity.model.CompanyInfoSection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CompanyAllSectionsParser {
    private final CompanySectionParser sectionParser;

    public CompanyAllSectionsParser(CompanySectionParser sectionParser) {
        this.sectionParser = sectionParser;
    }

    public Map<CompanyInfoSection, CompanySectionParser.ParsedSection> parse(String text) {
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException("请粘贴官网企业信息全文");
        EnumMap<CompanyInfoSection, List<StringBuilder>> blocks = new EnumMap<>(CompanyInfoSection.class);
        for (CompanyInfoSection section : CompanyInfoSection.values()) blocks.put(section, new ArrayList<>());
        blocks.get(CompanyInfoSection.BASIC).add(new StringBuilder());
        CompanyInfoSection current = CompanyInfoSection.BASIC;
        for (String originalLine : text.replace('\u00a0', ' ').split("\\R")) {
            String line = originalLine.trim();
            if (!StringUtils.hasText(line)) continue;
            CompanyInfoSection heading = headingSection(line);
            if (heading != null) {
                current = heading;
                blocks.get(current).add(new StringBuilder());
            } else if (isWebsiteBoundary(line)) {
                current = null;
            }
            if (current != null) currentBlock(blocks.get(current)).append(line).append('\n');
        }
        EnumMap<CompanyInfoSection, CompanySectionParser.ParsedSection> result = new EnumMap<>(CompanyInfoSection.class);
        for (CompanyInfoSection section : CompanyInfoSection.values()) {
            result.put(section, merge(section, blocks.get(section)));
        }
        return result;
    }

    private CompanySectionParser.ParsedSection merge(CompanyInfoSection section, List<StringBuilder> blocks) {
        List<CompanySectionParser.ParsedSection> parsed = blocks.stream().map(StringBuilder::toString)
                .map(String::trim).filter(StringUtils::hasText).map(text -> sectionParser.parse(section, text)).toList();
        if (parsed.isEmpty()) return new CompanySectionParser.ParsedSection("NOT_FOUND", List.of());
        if (section == CompanyInfoSection.BASIC) {
            return parsed.stream().max(java.util.Comparator.comparingInt(this::basicScore)).orElse(parsed.get(0));
        }
        List<Map<String, String>> records = parsed.stream().filter(item -> "PARSED".equals(item.status()))
                .flatMap(item -> item.records().stream()).toList();
        if (!records.isEmpty()) return new CompanySectionParser.ParsedSection("PARSED", records);
        if (parsed.stream().anyMatch(item -> "NO_RECORD".equals(item.status()))) {
            return new CompanySectionParser.ParsedSection("NO_RECORD", List.of());
        }
        return parsed.stream().filter(item -> !item.records().isEmpty()).findFirst()
                .orElse(new CompanySectionParser.ParsedSection("NOT_FOUND", List.of()));
    }

    private int basicScore(CompanySectionParser.ParsedSection parsed) {
        if (parsed.records().isEmpty()) return 0;
        Map<String, String> record = parsed.records().get(0);
        int score = record.size();
        if (StringUtils.hasText(record.get("companyName"))) score += 20;
        if (StringUtils.hasText(record.get("creditCode"))) score += 20;
        return score;
    }

    private StringBuilder currentBlock(List<StringBuilder> blocks) {
        if (blocks.isEmpty()) blocks.add(new StringBuilder());
        return blocks.get(blocks.size() - 1);
    }

    private CompanyInfoSection headingSection(String line) {
        String normalized = normalize(line);
        if (normalized.length() > 70) return null;
        Set<CompanyInfoSection> matches = new LinkedHashSet<>();
        for (CompanyInfoSection section : CompanyInfoSection.values()) {
            for (String alias : aliases(section)) {
                if (normalized.contains(normalize(alias))) { matches.add(section); break; }
            }
        }
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    private String normalize(String value) {
        return value.replaceAll("[\\s:：|｜（）()【】\\[\\]0-9]+", "").trim();
    }

    /**
     * A full GSXT copy contains many unrelated modules. They must end the
     * preceding target section instead of being treated as additional rows.
     */
    private boolean isWebsiteBoundary(String line) {
        String compact = normalize(line);
        if (compact.length() > 45 || compact.startsWith("提示")) return false;
        return compact.matches(".*(变更信息|行政许可信息|行政处罚信息|企业年报信息|分支机构信息|清算信息|"
                + "多证合一|营业期限信息|另册管理|信誉信息|知识产权|商标注册信息|名称转让信息|"
                + "动产抵押|股权出质|司法协助|双随机|产品质量|认证监管|食品抽查|其他抽查|"
                + "承诺不实|集团成员|执行标准|信用承诺|名称授权|歇业公告|冒用他人身份|拟强制注销).*");
    }

    private String[] aliases(CompanyInfoSection section) {
        return switch (section) {
            case BASIC -> new String[]{"企业基本信息", "登记信息", "基本信息", "基础信息", "营业执照信息", "照面信息"};
            case SHAREHOLDERS -> new String[]{"股东及出资信息", "股东及出资", "股东信息", "股东（发起人）", "发起人及出资"};
            case INVESTMENTS -> new String[]{"对外投资信息", "对外投资", "投资企业信息"};
            case POSITIONS -> new String[]{"主要人员信息", "主要人员", "主要成员", "任职信息", "董事监事经理信息"};
            case ABNORMAL -> new String[]{"列入经营异常名录信息", "经营异常名录信息", "经营异常名录", "经营异常信息"};
            case SERIOUS_VIOLATIONS -> new String[]{"列入严重违法失信名单（黑名单）信息", "严重违法失信名单信息", "严重违法失信名单", "严重违法失信信息"};
        };
    }
}
