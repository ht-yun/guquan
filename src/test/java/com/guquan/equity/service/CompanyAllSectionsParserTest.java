package com.guquan.equity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.guquan.equity.model.CompanyInfoSection;
import org.junit.jupiter.api.Test;

class CompanyAllSectionsParserTest {
    private final CompanyAllSectionsParser parser = new CompanyAllSectionsParser(new CompanySectionParser());

    @Test
    void splitsRealChineseHeadingsAndParsesBasicFields() {
        String text = "企业基本信息\n企业名称：示例科技有限公司\n统一社会信用代码：91310000MA12345678\n"
                + "法定代表人：张三\n登记状态：存续\n股东及出资信息\n股东\t认缴出资额\n李四\t100万元\n"
                + "对外投资信息\n被投资企业名称\t投资比例\n示例子公司\t100%\n主要人员信息\n姓名\t职务\n王五\t经理\n"
                + "经营异常名录信息\n暂无相关记录\n严重违法失信名单信息\n暂无相关信息";
        var result = parser.parse(text);
        assertEquals("PARSED", result.get(CompanyInfoSection.BASIC).status());
        assertEquals("示例科技有限公司", result.get(CompanyInfoSection.BASIC).records().get(0).get("companyName"));
        assertEquals("PARSED", result.get(CompanyInfoSection.SHAREHOLDERS).status());
        assertEquals("PARSED", result.get(CompanyInfoSection.INVESTMENTS).status());
        assertEquals("PARSED", result.get(CompanyInfoSection.POSITIONS).status());
        assertEquals("NO_RECORD", result.get(CompanyInfoSection.ABNORMAL).status());
        assertEquals("NO_RECORD", result.get(CompanyInfoSection.SERIOUS_VIOLATIONS).status());
    }

    @Test
    void noRecordElsewhereDoesNotEraseBasicInformation() {
        String text = "企业名称\n示例公司\n统一社会信用代码\n91310000MA12345678\n经营状态\n存续\n暂无经营异常记录";
        var result = parser.parse(text);
        assertNotEquals("NO_RECORD", result.get(CompanyInfoSection.BASIC).status());
        assertEquals("示例公司", result.get(CompanyInfoSection.BASIC).records().get(0).get("companyName"));
    }

    @Test
    void acceptsAlternativeWebsiteHeadings() {
        String text = "照面信息\n企业名称：示例公司\n股东（发起人）\n股东名称\t认缴出资额\n张三\t20万元\n主要成员\n姓名\t职务\n李四\t执行董事";
        var result = parser.parse(text);
        assertEquals("PARSED", result.get(CompanyInfoSection.BASIC).status());
        assertEquals("PARSED", result.get(CompanyInfoSection.SHAREHOLDERS).status());
        assertEquals("PARSED", result.get(CompanyInfoSection.POSITIONS).status());
    }
}
