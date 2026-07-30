package com.guquan.equity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guquan.equity.model.CompanyInfoSection;
import org.junit.jupiter.api.Test;

class CompanySectionParserTest {
    private final CompanySectionParser parser = new CompanySectionParser();

    @Test
    void parsesTabularShareholderText() {
        var result = parser.parse(CompanyInfoSection.SHAREHOLDERS,
                "股东\t认缴出资额\t出资日期\n张三\t100万元\t2024-01-01\n李四\t50万元\t2024-02-01");
        assertEquals("PARSED", result.status());
        assertEquals(2, result.records().size());
        assertEquals("张三", result.records().get(0).get("股东"));
    }

    @Test
    void parsesVerticallyCopiedTableCells() {
        var result = parser.parse(CompanyInfoSection.POSITIONS,
                "主要人员信息\n姓名\n职务\n张三\n执行董事\n李四\n监事");
        assertEquals("PARSED", result.status());
        assertEquals(2, result.records().size());
        assertEquals("执行董事", result.records().get(0).get("职务"));
    }

    @Test
    void distinguishesNoRecordOnlyForNonBasicSections() {
        assertEquals("NO_RECORD", parser.parse(CompanyInfoSection.ABNORMAL, "暂无经营异常记录").status());
        assertEquals("PARSED", parser.parse(CompanyInfoSection.BASIC,
                "企业名称：示例公司\n统一社会信用代码：91310000MA12345678\n暂无经营异常记录").status());
    }

    @Test
    void keepsActualRecordsWhenOneFieldSaysNoRelatedInformation() {
        var result = parser.parse(CompanyInfoSection.ABNORMAL,
                "列入经营异常名录原因\t列入日期\t作出决定机关\n"
                        + "未按规定公示年度报告\t2024-07-01\t北京市市场监督管理局\n"
                        + "移出经营异常名录原因\t暂无相关信息");

        assertEquals("PARSED", result.status());
        assertEquals("未按规定公示年度报告",
                result.records().get(0).get("列入经营异常名录原因"));
    }

    @Test
    void parsesPersonnelCardsWithInterleavedEncodedFragments() {
        var result = parser.parse(CompanyInfoSection.POSITIONS,
                "主要人员信息\n共计 3 条信息\n刘5YiY5Li65bib为5YiY5Li65bib帛\n"
                        + "郗5a6c5ZCb郗宜6YOX5a6c5ZCb君\n王546L5Lyg6ZyW传霖\n分支机构信息");

        assertEquals("PARSED", result.status());
        assertEquals(3, result.records().size());
        assertEquals("刘为帛", result.records().get(0).get("姓名"));
        assertEquals("郗宜君", result.records().get(1).get("姓名"));
        assertEquals("王传霖", result.records().get(2).get("姓名"));
    }

    @Test
    void ignoresTheSecondHeaderRowInAnnualReportShareholderTables() {
        var result = parser.parse(CompanyInfoSection.SHAREHOLDERS,
                "股东\t认缴额（万元）\t实缴额（万元）\t认缴明细\t实缴明细\n"
                        + "认缴出资方式\t认缴出资金额(万元)\t认缴出资日期\t公示日期\t实缴出资方式\t实缴出资额(万元)\n"
                        + "示例投资有限公司\t100\t100\t\t");

        assertEquals("PARSED", result.status());
        assertEquals(1, result.records().size());
        assertEquals("示例投资有限公司", result.records().get(0).get("股东"));
    }
}
