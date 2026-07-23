package com.guquan.equity.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class EmploymentTestWorkbookFactory {

    public static final String COMPANY_DISPLAY_NAME = "深圳示例科技（有限）公司";
    public static final String COMPANY_ALT_NAME = "深圳示例科技(有限)公司";
    public static final String CREDIT_CODE = "914403001922038216";

    private EmploymentTestWorkbookFactory() {
    }

    public static byte[] bytes(boolean withConflict) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet employment = workbook.createSheet("学生就业数据");
            XSSFRow note = employment.createRow(0);
            note.createCell(0).setCellValue("录入时间");
            note.createCell(1).setCellValue("匿名测试模板，请勿删除说明行");
            employment.addMergedRegion(new CellRangeAddress(0, 1, 0, 0));
            employment.addMergedRegion(new CellRangeAddress(0, 0, 1, 10));
            String[] employmentHeaders = {"考生号", "姓名", "毕业去向状态", "毕业去向类型", "单位名称",
                    "单位统一社会信用代码", "单位行业", "单位性质", "单位所在地代码", "单位所在地"};
            XSSFRow header = employment.createRow(1);
            for (int i = 0; i < employmentHeaders.length; i++) {
                header.createCell(i + 1).setCellValue(employmentHeaders[i]);
            }
            writeEmploymentRow(employment.createRow(2), "1001", "测试甲", COMPANY_DISPLAY_NAME);
            writeEmploymentRow(employment.createRow(3), "1002", "测试乙", COMPANY_ALT_NAME);
            if (withConflict) {
                employment.getRow(2).createCell(7).setCellValue("制造业");
            }
            employment.setColumnWidth(5, 38 * 256);

            XSSFSheet students = workbook.createSheet("学生基础信息");
            students.createRow(0).createCell(0).setCellValue("学生姓名");
            students.getRow(0).createCell(1).setCellValue("考生号");
            students.createRow(1).createCell(0).setCellValue("测试甲");
            students.getRow(1).createCell(1).setCellValue("1001");
            students.createRow(2).createCell(0).setCellValue("测试乙");
            students.getRow(2).createCell(1).setCellValue("1002");

            XSSFSheet areas = workbook.createSheet("地区代码");
            areas.createRow(0).createCell(0).setCellValue("地区代码");
            areas.getRow(0).createCell(1).setCellValue("显示名称");
            areas.createRow(1).createCell(0).setCellValue("440307");
            areas.getRow(1).createCell(1).setCellValue("深圳市龙岗区");
            areas.createRow(2).createCell(0).setCellValue("110108");
            areas.getRow(2).createCell(1).setCellValue("北京市海淀区");

            XSSFSheet codes = workbook.createSheet("代码表");
            codes.createRow(0).createCell(0).setCellValue("单位行业");
            codes.getRow(0).createCell(1).setCellValue("单位性质");
            codes.createRow(1).createCell(0).setCellValue("信息传输、软件和信息技术服务业");
            codes.getRow(1).createCell(1).setCellValue("其他企业（含民营企业等）");
            codes.createRow(2).createCell(0).setCellValue("制造业");
            codes.getRow(2).createCell(1).setCellValue("国有企业");

            workbook.createSheet("字段填写说明").createRow(0).createCell(0).setCellValue("填写项");
            workbook.createSheet("留学院校字典表").createRow(0).createCell(0).setCellValue("留学院校外文名称");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public static Path write(Path path, boolean withConflict) throws IOException {
        Files.write(path, bytes(withConflict));
        return path;
    }

    private static void writeEmploymentRow(XSSFRow row, String candidateNumber, String name, String companyName) {
        row.createCell(1).setCellValue(candidateNumber);
        row.createCell(2).setCellValue(name);
        row.createCell(3).setCellValue("就业");
        row.createCell(4).setCellValue("签劳动合同形式就业");
        row.createCell(5).setCellValue(companyName);
    }
}
