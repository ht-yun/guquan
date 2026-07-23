package com.guquan.equity.service;

import com.guquan.equity.model.EmploymentAreaOption;
import com.guquan.equity.provider.EmploymentJobProperties;
import com.guquan.equity.util.CompanyNameNormalizer;
import com.guquan.equity.util.UnifiedSocialCreditCodeUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmploymentWorkbookService {

    public static final String EMPLOYMENT_SHEET = "学生就业数据";
    public static final String STUDENT_SHEET = "学生基础信息";
    public static final String AREA_SHEET = "地区代码";
    public static final String CODE_SHEET = "代码表";
    public static final String INSTRUCTIONS_SHEET = "字段填写说明";
    public static final String OVERSEAS_SHEET = "留学院校字典表";

    public static final String COMPANY_NAME = "单位名称";
    public static final String CREDIT_CODE = "单位统一社会信用代码";
    public static final String INDUSTRY = "单位行业";
    public static final String UNIT_NATURE = "单位性质";
    public static final String AREA_CODE = "单位所在地代码";
    public static final String AREA_NAME = "单位所在地";

    private static final int MAX_VALIDATION_ERRORS = 200;
    private static final List<String> OUTPUT_FIELDS = List.of(
            COMPANY_NAME, CREDIT_CODE, INDUSTRY, UNIT_NATURE, AREA_CODE, AREA_NAME);

    private final EmploymentJobProperties properties;

    public EmploymentWorkbookService(EmploymentJobProperties properties) {
        this.properties = properties;
    }

    public ParsedWorkbook parse(Path path) {
        try (InputStream input = Files.newInputStream(path); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            Sheet employment = requireSheet(workbook, EMPLOYMENT_SHEET);
            Sheet students = requireSheet(workbook, STUDENT_SHEET);
            Sheet areas = requireSheet(workbook, AREA_SHEET);
            Sheet codes = requireSheet(workbook, CODE_SHEET);
            requireSheet(workbook, INSTRUCTIONS_SHEET);
            requireSheet(workbook, OVERSEAS_SHEET);

            Map<String, Integer> employmentHeaders = headers(employment.getRow(1));
            for (String required : List.of("考生号", "姓名", "毕业去向状态", "毕业去向类型",
                    COMPANY_NAME, CREDIT_CODE, INDUSTRY, UNIT_NATURE, AREA_CODE, AREA_NAME)) {
                requireHeader(employmentHeaders, required, EMPLOYMENT_SHEET);
            }
            ReferenceData references = readReferences(areas, codes);
            Map<String, String> roster = readRoster(students);
            List<EmploymentRecord> records = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (int rowIndex = 2; rowIndex <= employment.getLastRowNum(); rowIndex++) {
                Row row = employment.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String companyName = text(row.getCell(employmentHeaders.get(COMPANY_NAME)));
                if (!StringUtils.hasText(companyName)) {
                    continue;
                }
                if (records.size() >= properties.getMaxEmploymentRows()) {
                    throw new IllegalArgumentException("就业记录超过允许的最大行数：" + properties.getMaxEmploymentRows());
                }
                String candidateNumber = text(row.getCell(employmentHeaders.get("考生号")));
                String studentName = text(row.getCell(employmentHeaders.get("姓名")));
                validateStudent(rowIndex + 1, candidateNumber, studentName, roster, errors);
                records.add(new EmploymentRecord(
                        rowIndex + 1,
                        candidateNumber,
                        studentName,
                        companyName.trim(),
                        CompanyNameNormalizer.normalize(companyName)));
            }
            return new ParsedWorkbook(records, references, limitErrors(errors));
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取 Excel 文件，请确认文件未损坏且格式为 .xlsx", ex);
        }
    }

    public ReferenceData readReferences(Path path) {
        try (InputStream input = Files.newInputStream(path); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            return readReferences(requireSheet(workbook, AREA_SHEET), requireSheet(workbook, CODE_SHEET));
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取模板字典", ex);
        }
    }

    public List<EmploymentAreaOption> searchAreas(Path path, String query) {
        ReferenceData references = readReferences(path);
        String keyword = StringUtils.hasText(query) ? query.trim() : "";
        return references.areaNames().entrySet().stream()
                .filter(entry -> keyword.isEmpty()
                        || entry.getKey().contains(keyword)
                        || entry.getValue().contains(keyword))
                .limit(20)
                .map(entry -> new EmploymentAreaOption(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Set<String> findConflicts(Path path, List<Integer> rowNumbers, CompanyValues values) {
        try (InputStream input = Files.newInputStream(path); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = requireSheet(workbook, EMPLOYMENT_SHEET);
            Map<String, Integer> columns = headers(sheet.getRow(1));
            Set<String> conflicts = new LinkedHashSet<>();
            for (Integer rowNumber : rowNumbers) {
                Row row = sheet.getRow(rowNumber - 1);
                if (row == null) {
                    continue;
                }
                compare(conflicts, COMPANY_NAME, text(row.getCell(columns.get(COMPANY_NAME))),
                        values.replaceOfficialName() ? values.companyName() : null);
                compare(conflicts, CREDIT_CODE, text(row.getCell(columns.get(CREDIT_CODE))), values.creditCode());
                compare(conflicts, INDUSTRY, text(row.getCell(columns.get(INDUSTRY))), values.industry());
                compare(conflicts, UNIT_NATURE, text(row.getCell(columns.get(UNIT_NATURE))), values.unitNature());
                compare(conflicts, AREA_CODE, text(row.getCell(columns.get(AREA_CODE))), values.areaCode());
                compare(conflicts, AREA_NAME, text(row.getCell(columns.get(AREA_NAME))), values.areaName());
            }
            return conflicts;
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法检查模板中的已有值", ex);
        }
    }

    public void export(Path inputPath, Path outputPath, Collection<ExportGroup> groups) {
        try {
            Files.createDirectories(outputPath.getParent());
            try (InputStream input = Files.newInputStream(inputPath);
                    XSSFWorkbook workbook = new XSSFWorkbook(input);
                    OutputStream output = Files.newOutputStream(outputPath)) {
                Sheet sheet = requireSheet(workbook, EMPLOYMENT_SHEET);
                Map<String, Integer> columns = headers(sheet.getRow(1));
                for (String field : OUTPUT_FIELDS) {
                    requireHeader(columns, field, EMPLOYMENT_SHEET);
                }
                for (ExportGroup group : groups) {
                    for (Integer rowNumber : group.rowNumbers()) {
                        Row row = sheet.getRow(rowNumber - 1);
                        if (row == null) {
                            row = sheet.createRow(rowNumber - 1);
                        }
                        if (group.values().replaceOfficialName()) {
                            write(row, columns.get(COMPANY_NAME), COMPANY_NAME,
                                    group.values().companyName(), group.conflictDecisions());
                        }
                        write(row, columns.get(CREDIT_CODE), CREDIT_CODE,
                                group.values().creditCode(), group.conflictDecisions());
                        write(row, columns.get(INDUSTRY), INDUSTRY,
                                group.values().industry(), group.conflictDecisions());
                        write(row, columns.get(UNIT_NATURE), UNIT_NATURE,
                                group.values().unitNature(), group.conflictDecisions());
                        write(row, columns.get(AREA_CODE), AREA_CODE,
                                group.values().areaCode(), group.conflictDecisions());
                        write(row, columns.get(AREA_NAME), AREA_NAME,
                                group.values().areaName(), group.conflictDecisions());
                    }
                }
                workbook.write(output);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("导出 Excel 文件失败", ex);
        }
    }

    public List<String> validateCompanyFields(Path path) {
        ParsedWorkbook parsed = parse(path);
        List<String> errors = new ArrayList<>(parsed.validationErrors());
        try (InputStream input = Files.newInputStream(path); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = requireSheet(workbook, EMPLOYMENT_SHEET);
            Map<String, Integer> columns = headers(sheet.getRow(1));
            for (EmploymentRecord record : parsed.records()) {
                Row row = sheet.getRow(record.rowNumber() - 1);
                String creditCode = value(row, columns, CREDIT_CODE);
                String industry = value(row, columns, INDUSTRY);
                String nature = value(row, columns, UNIT_NATURE);
                String areaCode = value(row, columns, AREA_CODE);
                String areaName = value(row, columns, AREA_NAME);
                if (!UnifiedSocialCreditCodeUtil.parse(creditCode).isValid()) {
                    addError(errors, record.rowNumber(), "统一社会信用代码为空或校验不通过");
                }
                if (!parsed.references().industries().contains(industry)) {
                    addError(errors, record.rowNumber(), "单位行业不在模板代码表中");
                }
                if (!parsed.references().unitNatures().contains(nature)) {
                    addError(errors, record.rowNumber(), "单位性质不在模板代码表中");
                }
                String expectedAreaName = parsed.references().areaNames().get(areaCode);
                if (expectedAreaName == null) {
                    addError(errors, record.rowNumber(), "单位所在地代码不在地区代码表中");
                } else if (!expectedAreaName.equals(areaName)) {
                    addError(errors, record.rowNumber(), "单位所在地与所在地代码不一致");
                }
            }
            return limitErrors(errors);
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法校验导出文件", ex);
        }
    }

    private ReferenceData readReferences(Sheet areas, Sheet codes) {
        Map<String, Integer> areaHeaders = headers(areas.getRow(0));
        int areaCodeColumn = requireHeader(areaHeaders, "地区代码", AREA_SHEET);
        int areaNameColumn = requireHeader(areaHeaders, "显示名称", AREA_SHEET);
        Map<String, String> areaNames = new LinkedHashMap<>();
        for (int i = 1; i <= areas.getLastRowNum(); i++) {
            Row row = areas.getRow(i);
            if (row == null) {
                continue;
            }
            String code = text(row.getCell(areaCodeColumn));
            String name = text(row.getCell(areaNameColumn));
            if (code.matches("\\d{6}") && StringUtils.hasText(name)) {
                areaNames.put(code, name.trim());
            }
        }

        Map<String, Integer> codeHeaders = headers(codes.getRow(0));
        int industryColumn = requireHeader(codeHeaders, INDUSTRY, CODE_SHEET);
        int natureColumn = requireHeader(codeHeaders, UNIT_NATURE, CODE_SHEET);
        Set<String> industries = columnValues(codes, industryColumn);
        Set<String> unitNatures = columnValues(codes, natureColumn);
        if (areaNames.isEmpty() || industries.isEmpty() || unitNatures.isEmpty()) {
            throw new IllegalArgumentException("模板中的地区代码或单位分类字典为空");
        }
        return new ReferenceData(areaNames, industries, unitNatures);
    }

    private Map<String, String> readRoster(Sheet sheet) {
        Map<String, Integer> columns = headers(sheet.getRow(0));
        int candidateColumn = requireHeader(columns, "考生号", STUDENT_SHEET);
        int nameColumn = requireHeader(columns, "学生姓名", STUDENT_SHEET);
        Map<String, String> roster = new LinkedHashMap<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String candidateNumber = text(row.getCell(candidateColumn));
            String name = text(row.getCell(nameColumn));
            if (StringUtils.hasText(candidateNumber)) {
                roster.put(candidateNumber.trim(), name.trim());
            }
        }
        return roster;
    }

    private void validateStudent(
            int rowNumber,
            String candidateNumber,
            String studentName,
            Map<String, String> roster,
            List<String> errors) {
        if (!StringUtils.hasText(candidateNumber)) {
            addError(errors, rowNumber, "考生号不能为空");
            return;
        }
        String expectedName = roster.get(candidateNumber.trim());
        if (expectedName == null) {
            addError(errors, rowNumber, "考生号未在学生基础信息中找到");
            return;
        }
        if (!normalizePersonName(expectedName).equals(normalizePersonName(studentName))) {
            addError(errors, rowNumber, "姓名与学生基础信息不一致");
        }
    }

    private String normalizePersonName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private void compare(Set<String> conflicts, String field, String existing, String replacement) {
        if (!StringUtils.hasText(existing) || !StringUtils.hasText(replacement)) {
            return;
        }
        boolean same = COMPANY_NAME.equals(field)
                ? CompanyNameNormalizer.normalize(existing).equals(CompanyNameNormalizer.normalize(replacement))
                : existing.trim().equals(replacement.trim());
        if (!same) {
            conflicts.add(field);
        }
    }

    private void write(Row row, int column, String field, String value, Map<String, String> decisions) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        String existing = text(cell);
        if (!StringUtils.hasText(existing)
                || existing.trim().equals(value.trim())
                || "USE_QUERY".equals(decisions.get(field))) {
            cell.setCellValue(value.trim());
        }
    }

    private String value(Row row, Map<String, Integer> columns, String field) {
        return row == null ? "" : text(row.getCell(columns.get(field)));
    }

    private Set<String> columnValues(Sheet sheet, int column) {
        Set<String> values = new LinkedHashSet<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String value = row == null ? "" : text(row.getCell(column));
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private Sheet requireSheet(XSSFWorkbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new IllegalArgumentException("缺少工作表：" + name);
        }
        return sheet;
    }

    private Map<String, Integer> headers(Row row) {
        if (row == null) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Cell cell : row) {
            String value = text(cell);
            if (StringUtils.hasText(value)) {
                result.putIfAbsent(value.trim(), cell.getColumnIndex());
            }
        }
        return result;
    }

    private int requireHeader(Map<String, Integer> headers, String header, String sheetName) {
        Integer column = headers.get(header);
        if (column == null) {
            throw new IllegalArgumentException("工作表“" + sheetName + "”缺少列：" + header);
        }
        return column;
    }

    private String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        return new DataFormatter(Locale.CHINA).formatCellValue(cell).trim();
    }

    private void addError(List<String> errors, int rowNumber, String message) {
        if (errors.size() < MAX_VALIDATION_ERRORS) {
            errors.add("第 " + rowNumber + " 行：" + message);
        }
    }

    private List<String> limitErrors(List<String> errors) {
        return errors.stream().limit(MAX_VALIDATION_ERRORS).collect(Collectors.toList());
    }

    public record EmploymentRecord(
            int rowNumber,
            String candidateNumber,
            String studentName,
            String companyName,
            String normalizedCompanyName) {
    }

    public record ParsedWorkbook(
            List<EmploymentRecord> records,
            ReferenceData references,
            List<String> validationErrors) {
    }

    public record ReferenceData(
            Map<String, String> areaNames,
            Set<String> industries,
            Set<String> unitNatures) {

        public List<Map.Entry<String, String>> areasByNameLength() {
            return areaNames.entrySet().stream()
                    .sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getValue().length())
                            .reversed())
                    .toList();
        }
    }

    public record CompanyValues(
            String companyName,
            String creditCode,
            String industry,
            String unitNature,
            String areaCode,
            String areaName,
            boolean replaceOfficialName) {
    }

    public record ExportGroup(
            List<Integer> rowNumbers,
            CompanyValues values,
            Map<String, String> conflictDecisions) {
    }
}
