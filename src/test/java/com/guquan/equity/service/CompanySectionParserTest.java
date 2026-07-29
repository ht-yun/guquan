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
}
