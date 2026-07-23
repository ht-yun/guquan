package com.guquan.equity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guquan.equity.model.CompanyBrowserTaskRequest;
import org.junit.jupiter.api.Test;

class GsxtPageParserTest {

    private final GsxtPageParser parser = new GsxtPageParser();

    @Test
    void parsesEachCompanyFromItsOwnTextBlock() {
        String body = """
                华为技术有限公司
                统一社会信用代码：914403001922038216
                法定代表人：任正非
                所属行业：软件和信息技术服务业
                企业类型：有限责任公司
                注册地址行政区划代码：440307
                住所：深圳市龙岗区
                阿里巴巴（中国）有限公司
                统一社会信用代码：91330100799655058B
                法定代表人：张勇
                所属行业：互联网和相关服务
                企业类型：有限责任公司（台港澳法人独资）
                注册地址行政区划代码：330110
                住所：杭州市余杭区
                """;

        var profiles = parser.parse(body, new CompanyBrowserTaskRequest());

        assertThat(profiles).hasSize(2);
        assertThat(profiles.get(0).getCompanyName()).isEqualTo("华为技术有限公司");
        assertThat(profiles.get(0).getIndustryName()).isEqualTo("软件和信息技术服务业");
        assertThat(profiles.get(0).getRegisteredAddressAreaCode()).isEqualTo("440307");
        assertThat(profiles.get(1).getCompanyName()).isEqualTo("阿里巴巴（中国）有限公司");
        assertThat(profiles.get(1).getIndustryName()).isEqualTo("互联网和相关服务");
        assertThat(profiles.get(1).getRegisteredAddressAreaCode()).isEqualTo("330110");
    }

    @Test
    void ignoresInvalidEighteenCharacterIdentifiers() {
        String body = "某单位\n统一社会信用代码：914403001922038210\n";

        assertThat(parser.parse(body, new CompanyBrowserTaskRequest())).isEmpty();
    }

    @Test
    void parsesCopiedDetailTextWithLabelsAndValuesOnSeparateLines() {
        String body = """
                华为技术有限公司
                统一社会信用代码
                914403001922038216
                法定代表人
                任正非
                所属行业
                计算机、通信和其他电子设备制造业
                企业类型
                有限责任公司
                注册地址行政区划代码
                440307
                住所
                深圳市龙岗区坂田华为总部办公楼
                """;

        var profiles = parser.parse(body, new CompanyBrowserTaskRequest(), "GSXT_MANUAL_IMPORT");

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).getCompanyName()).isEqualTo("华为技术有限公司");
        assertThat(profiles.get(0).getLegalPerson()).isEqualTo("任正非");
        assertThat(profiles.get(0).getIndustryName()).isEqualTo("计算机、通信和其他电子设备制造业");
        assertThat(profiles.get(0).getEntityType()).isEqualTo("有限责任公司");
        assertThat(profiles.get(0).getRegisteredAddressAreaCode()).isEqualTo("440307");
        assertThat(profiles.get(0).getSource()).isEqualTo("GSXT_MANUAL_IMPORT");
    }
}
