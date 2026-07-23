package com.guquan.equity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guquan.equity.model.CompanyProfile;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmploymentStandardizationServiceTest {

    private final EmploymentStandardizationService service = new EmploymentStandardizationService();

    @Test
    void mapsIndustryNatureAndAddressToTemplateValues() {
        var references = references();
        CompanyProfile profile = CompanyProfile.builder()
                .creditCode("914403001922038216")
                .industryName("软件和信息技术服务业")
                .entityType("有限责任公司（自然人投资或控股）")
                .registeredAddress("广东省深圳市龙岗区坂田街道测试路1号")
                .build();

        var suggestion = service.suggest(profile, references);

        assertThat(suggestion.industry()).isEqualTo("信息传输、软件和信息技术服务业");
        assertThat(suggestion.unitNature()).isEqualTo("其他企业（含民营企业等）");
        assertThat(suggestion.areaCode()).isEqualTo("440307");
        assertThat(suggestion.confidence()).isEqualTo("HIGH");
    }

    @Test
    void leavesAmbiguousCorporateOwnershipForReview() {
        CompanyProfile profile = CompanyProfile.builder()
                .creditCode("914403001922038216")
                .industryName("软件和信息技术服务业")
                .entityType("有限责任公司（非自然人投资或控股的法人独资）")
                .registeredAddressAreaCode("440307")
                .build();

        var suggestion = service.suggest(profile, references());

        assertThat(suggestion.unitNature()).isNull();
        assertThat(suggestion.confidence()).isEqualTo("REVIEW");
    }

    private EmploymentWorkbookService.ReferenceData references() {
        Map<String, String> areas = new LinkedHashMap<>();
        areas.put("440307", "深圳市龙岗区");
        Set<String> industries = new LinkedHashSet<>(Set.of("信息传输、软件和信息技术服务业", "制造业"));
        Set<String> natures = new LinkedHashSet<>(Set.of("其他企业（含民营企业等）", "国有企业"));
        return new EmploymentWorkbookService.ReferenceData(areas, industries, natures);
    }
}
