package com.guquan.equity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guquan.equity.provider.EmploymentJobProperties;
import com.guquan.equity.support.EmploymentTestWorkbookFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmploymentWorkbookServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesDeduplicatedNamesAndExportsWithoutChangingWorkbookStructure() throws Exception {
        Path input = EmploymentTestWorkbookFactory.write(tempDir.resolve("input.xlsx"), false);
        Path output = tempDir.resolve("output.xlsx");
        EmploymentWorkbookService service = service();

        var parsed = service.parse(input);
        assertThat(parsed.records()).hasSize(2);
        assertThat(parsed.records().get(0).normalizedCompanyName())
                .isEqualTo(parsed.records().get(1).normalizedCompanyName());
        assertThat(parsed.validationErrors()).isEmpty();

        var values = new EmploymentWorkbookService.CompanyValues(
                EmploymentTestWorkbookFactory.COMPANY_DISPLAY_NAME,
                EmploymentTestWorkbookFactory.CREDIT_CODE,
                "信息传输、软件和信息技术服务业",
                "其他企业（含民营企业等）",
                "440307",
                "深圳市龙岗区",
                false);
        service.export(input, output, List.of(new EmploymentWorkbookService.ExportGroup(
                List.of(3, 4), values, Map.of())));

        assertThat(service.validateCompanyFields(output)).isEmpty();
        try (InputStream stream = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(6);
            assertThat(workbook.getSheet("学生就业数据").getNumMergedRegions()).isEqualTo(2);
            assertThat(workbook.getSheet("学生就业数据").getRow(2).getCell(6).getStringCellValue())
                    .isEqualTo(EmploymentTestWorkbookFactory.CREDIT_CODE);
            assertThat(workbook.getSheet("学生就业数据").getRow(2).getCell(6).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(workbook.getSheet("学生就业数据").getColumnWidth(5)).isEqualTo(38 * 256);
        }
    }

    @Test
    void detectsExistingFieldConflict() throws Exception {
        Path input = EmploymentTestWorkbookFactory.write(tempDir.resolve("conflict.xlsx"), true);
        EmploymentWorkbookService service = service();
        var values = new EmploymentWorkbookService.CompanyValues(
                EmploymentTestWorkbookFactory.COMPANY_DISPLAY_NAME,
                EmploymentTestWorkbookFactory.CREDIT_CODE,
                "信息传输、软件和信息技术服务业",
                "其他企业（含民营企业等）",
                "440307",
                "深圳市龙岗区",
                false);

        assertThat(service.findConflicts(input, List.of(3, 4), values))
                .containsExactly("单位行业");
    }

    private EmploymentWorkbookService service() {
        EmploymentJobProperties properties = new EmploymentJobProperties();
        properties.setMaxEmploymentRows(100);
        return new EmploymentWorkbookService(properties);
    }
}
