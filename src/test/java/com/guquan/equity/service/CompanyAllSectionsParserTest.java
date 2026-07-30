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

    @Test
    void parsesAFullGsxtPageWithoutMixingUnrelatedModules() {
        String text = """
                首页 企业信用信息
                中铁一局集团有限公司 开业
                统一社会信用代码：91610000220522345A
                注册号： 法定代表人：郗宜君 登记机关：西安市碑林区市场监督管理局
                股东及出资信息
                营业执照信息
                统一社会信用代码：
                91610000220522345A
                企业名称：
                中铁一局集团有限公司
                法定代表人：
                郗宜君
                类型：
                有限责任公司（非自然人投资或控股的法人独资）
                成立日期：
                1980年11月24日
                注册资本：
                636601.090000万人民币
                登记机关：
                西安市碑林区市场监督管理局
                登记状态：
                开业
                住所：
                陕西省西安市碑林区雁塔北路1号
                经营范围：
                一般项目：工程管理服务；对外承包工程。
                股东及出资信息
                序号\t股东名称\t股东类型\t证照/证件类型
                1\t中国中铁股份有限公司\t法人股东\t企业法人营业执照(公司)
                共 查询到 1 条记录 共 1 页
                主要人员信息
                共计 3 条信息
                刘5YiY5Li65bib为5YiY5Li65bib帛
                郗5a6c5ZCb郗宜6YOX5a6c5ZCb君
                王546L5Lyg6ZyW传霖
                分支机构信息
                暂无分支机构信息
                变更信息
                序号\t变更事项
                1\t高级管理人员备案
                列入经营异常名录信息
                序号\t列入经营异常名录原因
                暂无列入经营异常名录信息
                列入严重违法失信名单（黑名单）信息
                序号\t列入严重违法失信名单（黑名单）原因
                暂无列入严重违法失信名单信息
                企业年报信息
                股东及出资信息
                股东\t认缴额（万元）
                中国中铁股份有限公司\t636601.09
                """;

        var result = parser.parse(text);

        var basic = result.get(CompanyInfoSection.BASIC).records().get(0);
        assertEquals("中铁一局集团有限公司", basic.get("companyName"));
        assertEquals("91610000220522345A", basic.get("creditCode"));
        assertEquals("郗宜君", basic.get("legalPerson"));
        assertEquals("开业", basic.get("registrationStatus"));
        assertEquals("中国中铁股份有限公司",
                result.get(CompanyInfoSection.SHAREHOLDERS).records().get(0).get("股东名称"));
        assertEquals(3, result.get(CompanyInfoSection.POSITIONS).records().size());
        assertEquals("郗宜君", result.get(CompanyInfoSection.POSITIONS).records().get(1).get("姓名"));
        assertEquals("NO_RECORD", result.get(CompanyInfoSection.ABNORMAL).status());
        assertEquals("NO_RECORD", result.get(CompanyInfoSection.SERIOUS_VIOLATIONS).status());
    }
}
