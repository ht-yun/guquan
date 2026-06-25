package com.guquan.equity.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guquan.equity.api.EquityDataProvider;
import com.guquan.equity.model.CompanyCandidate;
import com.guquan.equity.model.PersonHolding;
import com.guquan.equity.model.ShareholderHolding;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 企查查数据源。
 * 可查询：某人在所有工商注册公司中的股东身份及出资信息（不限于上市公司十大股东）。
 * <p>
 * 需先在 application.yml 中配置 app-key / app-secret，并将 enabled 设为 true。
 * 申请地址：https://open.qichacha.com/
 * </p>
 */
@Component
public class QichachaEquityDataProvider implements EquityDataProvider {

    private static final Logger log = LoggerFactory.getLogger(QichachaEquityDataProvider.class);

    static final String SOURCE = "QICHACHA";
    private static final String API_BASE = "https://api.qichacha.com";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final QichachaProperties properties;

    public QichachaEquityDataProvider(ObjectMapper objectMapper, QichachaProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return SOURCE;
    }

    @Override
    public List<CompanyCandidate> searchCompany(String companyName) {
        if (!properties.isAvailable() || companyName == null || companyName.isBlank()) {
            return List.of();
        }
        // 企查查企业搜索：Company/SearchCompany
        // 返回格式：{ Status: "OK", Result: { DataList: [...] } }
        try {
            String encoded = URLEncoder.encode(companyName.trim(), StandardCharsets.UTF_8);
            URI uri = URI.create(API_BASE + "/Company/SearchCompany?key=" + encoded + "&pageIndex=1&pageSize=20");
            JsonNode root = callApi(uri);
            if (!"OK".equals(text(root, "Status"))) {
                return List.of();
            }
            JsonNode dataList = root.path("Result").path("DataList");
            if (!dataList.isArray()) {
                return List.of();
            }
            List<CompanyCandidate> companies = new ArrayList<>();
            for (JsonNode item : dataList) {
                companies.add(CompanyCandidate.builder()
                        .companyName(text(item, "CompanyName"))
                        .creditCode(text(item, "CreditCode"))
                        .legalPerson(text(item, "OperName"))
                        .status(text(item, "RegStatus"))
                        .listedCompany(false)
                        .source(SOURCE)
                        .build());
            }
            return companies;
        } catch (RuntimeException ex) {
            log.warn("Qichacha company search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<CompanyCandidate> getCompanyByCreditCode(String creditCode) {
        return Optional.empty();
    }

    @Override
    public List<ShareholderHolding> getIndustrialRegistryHoldings(CompanyCandidate company) {
        return List.of();
    }

    @Override
    public List<ShareholderHolding> getListedCompanyHoldings(CompanyCandidate company) {
        return List.of();
    }

    @Override
    public List<PersonHolding> searchPersonHoldings(String personName) {
        if (!properties.isAvailable()) {
            return List.of();
        }
        if (personName == null || personName.isBlank()) {
            return List.of();
        }
        try {
            String encoded = URLEncoder.encode(personName.trim(), StandardCharsets.UTF_8);
            URI uri = URI.create(API_BASE + "/Person/SearchPersonDetails?searchKey=" + encoded
                    + "&pageIndex=1&pageSize=200");
            JsonNode root = callApi(uri);
            if (!"OK".equals(text(root, "Status"))) {
                log.warn("Qichacha person search failed: {}", text(root, "Message"));
                return List.of();
            }
            return parsePersonHoldings(root);
        } catch (RuntimeException ex) {
            log.warn("Qichacha personHoldings search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<PersonHolding> parsePersonHoldings(JsonNode root) {
        JsonNode dataList = root.path("Result").path("DataList");
        if (!dataList.isArray()) {
            return List.of();
        }
        List<PersonHolding> results = new ArrayList<>();
        for (JsonNode item : dataList) {
            String personType = text(item, "PersonType");
            // PersonType: "股东", "法人", "董事", "监事", "高管" 等
            // 只保留股东相关的类型
            if (personType == null || !personType.contains("股东")) {
                continue;
            }
            BigDecimal amount = parseAmount(text(item, "ShouldCapital"));
            BigDecimal ratio = parsePercent(text(item, "StockPercent"));
            LocalDate updatedAt = LocalDate.now();
            results.add(PersonHolding.builder()
                    .companyName(text(item, "CompanyName"))
                    .creditCode(text(item, "CreditCode"))
                    .shareholderName(text(item, "Name"))
                    .shareholderType(text(item, "StockTypeName"))
                    .holdingAmount(amount)
                    .holdingRatio(ratio)
                    .currency("人民币元")
                    .dataSource(SOURCE)
                    .sourceUpdatedAt(updatedAt)
                    .build());
        }
        return results;
    }

    private JsonNode callApi(URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Token", properties.getAppKey());
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        String body = restTemplate.exchange(
                RequestEntity.get(uri).headers(headers).build(), String.class).getBody();
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Qichacha JSON response", ex);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").replace(" ", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parsePercent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(value.replace("%", "").replace(" ", ""));
            // Qichacha returns 50 for 50%, convert to decimal 0.5
            return v.divide(new BigDecimal("100"));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
