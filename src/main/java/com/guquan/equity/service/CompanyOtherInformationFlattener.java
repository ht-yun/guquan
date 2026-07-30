package com.guquan.equity.service;

import com.guquan.equity.model.CompanyInfoSection;
import com.guquan.equity.model.CompanyOtherInformationItem;
import com.guquan.equity.repository.CompanyBatchCompanyEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Converts one-to-many parsed section records into an exportable information list. */
@Component
public class CompanyOtherInformationFlattener {

    private static final Set<String> MAIN_BASIC_FIELDS = Set.of(
            "companyName", "creditCode", "legalPerson", "registrationStatus",
            "industry", "entityType", "registeredAddress");
    private static final Map<String, String> BASIC_FIELD_NAMES = basicFieldNames();

    public List<CompanyOtherInformationItem> flatten(CompanyBatchCompanyEntity company,
            CompanyInfoSection section, String status, List<Map<String, String>> records) {
        List<CompanyOtherInformationItem> result = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            result.add(item(company, section, status, 0, "栏目状态", statusLabel(status)));
            return result;
        }
        int number = 0;
        for (Map<String, String> record : records) {
            number++;
            int before = result.size();
            for (Map.Entry<String, String> field : record.entrySet()) {
                if (!StringUtils.hasText(field.getValue()) || "rawText".equals(field.getKey())
                        || (section == CompanyInfoSection.BASIC && MAIN_BASIC_FIELDS.contains(field.getKey()))) {
                    continue;
                }
                result.add(item(company, section, status, number, fieldName(section, field.getKey()), field.getValue()));
            }
            if (before == result.size()) {
                result.add(item(company, section, status, number, "栏目状态", statusLabel(status)));
            }
        }
        return result;
    }

    private CompanyOtherInformationItem item(CompanyBatchCompanyEntity company, CompanyInfoSection section,
            String status, int recordNumber, String fieldName, String value) {
        return CompanyOtherInformationItem.builder().companyId(company.getId())
                .inputCompanyName(company.getInputCompanyName()).officialCompanyName(company.getOfficialCompanyName())
                .creditCode(company.getCreditCode()).section(section).sectionStatus(status)
                .recordNumber(recordNumber).fieldName(fieldName).value(value).build();
    }

    private String fieldName(CompanyInfoSection section, String field) {
        return section == CompanyInfoSection.BASIC ? BASIC_FIELD_NAMES.getOrDefault(field, field) : field;
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "NO_RECORD" -> "官网明确显示无记录";
            case "NOT_FOUND", "NOT_COLLECTED" -> "未采集到该栏目";
            case "PARTIAL" -> "仅部分解析，请结合原始页面文字复核";
            case "PARSED" -> "已解析，未发现可列入清单的额外字段";
            default -> StringUtils.hasText(status) ? status : "未采集到该栏目";
        };
    }

    private static Map<String, String> basicFieldNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("registeredCapital", "注册资本");
        names.put("establishedAt", "成立日期");
        names.put("registrationAuthority", "登记机关");
        names.put("businessScope", "经营范围");
        return Map.copyOf(names);
    }
}
