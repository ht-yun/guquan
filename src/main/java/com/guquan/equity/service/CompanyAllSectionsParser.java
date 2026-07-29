package com.guquan.equity.service;

import com.guquan.equity.model.CompanyInfoSection;
import java.util.EnumMap;
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
        EnumMap<CompanyInfoSection, StringBuilder> blocks = new EnumMap<>(CompanyInfoSection.class);
        for (CompanyInfoSection section : CompanyInfoSection.values()) blocks.put(section, new StringBuilder());
        CompanyInfoSection current = CompanyInfoSection.BASIC;
        for (String originalLine : text.replace('\u00a0', ' ').split("\\R")) {
            String line = originalLine.trim();
            if (!StringUtils.hasText(line)) continue;
            CompanyInfoSection heading = headingSection(line);
            if (heading != null) current = heading;
            blocks.get(current).append(line).append('\n');
        }
        EnumMap<CompanyInfoSection, CompanySectionParser.ParsedSection> result = new EnumMap<>(CompanyInfoSection.class);
        for (CompanyInfoSection section : CompanyInfoSection.values()) {
            String block = blocks.get(section).toString().trim();
            result.put(section, block.isEmpty()
                    ? new CompanySectionParser.ParsedSection("NOT_FOUND", java.util.List.of())
                    : sectionParser.parse(section, block));
        }
        return result;
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
