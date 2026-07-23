package com.guquan.equity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guquan.equity.support.EmploymentTestWorkbookFactory;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:employment-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "employment-jobs.storage-dir=target/test-employment-jobs",
        "company-browser.enabled=false"
})
class EmploymentJobControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadsImportsConfirmsAndExportsOneCompanyForTwoRows() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "匿名就业数据.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                EmploymentTestWorkbookFactory.bytes(false));

        String createBody = mockMvc.perform(multipart("/api/employment-jobs").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(2))
                .andExpect(jsonPath("$.uniqueCompanies").value(1))
                .andExpect(jsonPath("$.pendingCollection").value(1))
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(createBody).get("jobId").asText();

        String companiesBody = mockMvc.perform(get("/api/employment-jobs/{jobId}/companies", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].affectedRows").value(2))
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(companiesBody).get(0).get("companyId").asLong();

        String pageText = EmploymentTestWorkbookFactory.COMPANY_DISPLAY_NAME + "\n"
                + "统一社会信用代码 " + EmploymentTestWorkbookFactory.CREDIT_CODE + "\n"
                + "所属行业 软件和信息技术服务业\n"
                + "企业类型 有限责任公司（自然人投资或控股）\n"
                + "住所 广东省深圳市龙岗区坂田街道测试路1号";
        String importBody = mockMvc.perform(post(
                        "/api/employment-jobs/{jobId}/companies/{companyId}/import", jobId, companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ImportBody(pageText))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode profile = objectMapper.readTree(importBody).get("candidates").get(0).get("profile");

        String confirmJson = """
                {
                  "profile": %s,
                  "employmentIndustry": "信息传输、软件和信息技术服务业",
                  "employmentUnitNature": "其他企业（含民营企业等）",
                  "registeredAddressAreaCode": "440307",
                  "replaceOfficialName": false,
                  "conflictDecisions": {}
                }
                """.formatted(profile.toString());
        mockMvc.perform(post(
                        "/api/employment-jobs/{jobId}/companies/{companyId}/confirm", jobId, companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        byte[] exported = mockMvc.perform(get("/api/employment-jobs/{jobId}/export", jobId)
                        .param("mode", "final"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertThat(workbook.getSheet("学生就业数据").getRow(2).getCell(6).getStringCellValue())
                    .isEqualTo(EmploymentTestWorkbookFactory.CREDIT_CODE);
            assertThat(workbook.getSheet("学生就业数据").getRow(3).getCell(9).getStringCellValue())
                    .isEqualTo("440307");
        }

        mockMvc.perform(delete("/api/employment-jobs/{jobId}", jobId))
                .andExpect(status().isOk());
    }

    @Test
    void recordsPartialOfficialInformationAndDefersCompletionToReview() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "partial.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                EmploymentTestWorkbookFactory.bytes(false));
        String createBody = mockMvc.perform(multipart("/api/employment-jobs").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(createBody).get("jobId").asText();
        String companiesBody = mockMvc.perform(get("/api/employment-jobs/{jobId}/companies", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(companiesBody).get(0).get("companyId").asLong();

        String partialProfile = """
                {
                  "profile": {
                    "companyName": "%s",
                    "creditCode": "%s",
                    "entityType": "limited company",
                    "source": "GSXT_MANUAL_IMPORT"
                  }
                }
                """.formatted(
                EmploymentTestWorkbookFactory.COMPANY_DISPLAY_NAME,
                EmploymentTestWorkbookFactory.CREDIT_CODE);
        mockMvc.perform(post(
                        "/api/employment-jobs/{jobId}/companies/{companyId}/collect", jobId, companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partialProfile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.creditCode").value(EmploymentTestWorkbookFactory.CREDIT_CODE));

        mockMvc.perform(get("/api/employment-jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCollection").value(0))
                .andExpect(jsonPath("$.pendingReview").value(1));
    }

    private record ImportBody(String pageText) {
    }
}
