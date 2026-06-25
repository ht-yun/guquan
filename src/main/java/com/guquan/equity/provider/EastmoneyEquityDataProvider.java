package com.guquan.equity.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guquan.equity.api.EquityDataProvider;
import com.guquan.equity.model.CompanyCandidate;
import com.guquan.equity.model.PersonHolding;
import com.guquan.equity.model.ShareholderHolding;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Primary
@Component
public class EastmoneyEquityDataProvider implements EquityDataProvider {

    private static final String SOURCE = "EASTMONEY_PUBLIC";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public EastmoneyEquityDataProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return SOURCE;
    }

    @Override
    public List<CompanyCandidate> searchCompany(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return List.of();
        }
        try {
            String encoded = URLEncoder.encode(companyName.trim(), StandardCharsets.UTF_8);
            URI uri = URI.create("https://searchapi.eastmoney.com/api/suggest/get?input="
                    + encoded + "&type=14");
            JsonNode root = getJson(uri, "https://www.eastmoney.com/");
            JsonNode data = root.path("QuotationCodeTable").path("Data");
            if (!data.isArray()) {
                return List.of();
            }
            List<CompanyCandidate> companies = new ArrayList<>();
            for (JsonNode item : data) {
                if (!"AStock".equalsIgnoreCase(text(item, "Classify"))) {
                    continue;
                }
                String code = text(item, "Code");
                String secucode = toSecucode(text(item, "QuoteID"), code);
                if (secucode == null) {
                    continue;
                }
                companies.add(CompanyCandidate.builder()
                        .companyName(text(item, "Name"))
                        .creditCode(secucode)
                        .status(text(item, "SecurityTypeName"))
                        .listedCompany(true)
                        .stockCode(secucode)
                        .source(SOURCE)
                        .build());
            }
            return companies;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Eastmoney company search failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<CompanyCandidate> getCompanyByCreditCode(String creditCode) {
        if (creditCode == null || creditCode.isBlank()) {
            return Optional.empty();
        }
        String secucode = normalizeSecucode(creditCode);
        return Optional.of(CompanyCandidate.builder()
                .companyName(secucode)
                .creditCode(secucode)
                .status("上市")
                .listedCompany(true)
                .stockCode(secucode)
                .source(SOURCE)
                .build());
    }

    @Override
    public List<PersonHolding> searchPersonHoldings(String personName) {
        if (personName == null || personName.isBlank()) {
            return List.of();
        }
        String encoded = URLEncoder.encode(personName.trim(), StandardCharsets.UTF_8);
        List<PersonHolding> results = new ArrayList<>();
        // 尝试使用 datacenter API 按股东姓名搜索（不加 SECUCODE 过滤，实现跨公司搜索）
        try {
            results.addAll(fetchPersonHoldingsByReport(personName, "RPT_F10_EH_HOLDERS", "十大股东"));
            results.addAll(fetchPersonHoldingsByReport(personName, "RPT_F10_EH_FREEHOLDERS", "十大流通股东"));
        } catch (RuntimeException ex) {
            // 单个 API 失败不阻断整体流程；记录日志（可扩展）
        }
        return results;
    }

    private List<PersonHolding> fetchPersonHoldingsByReport(String personName, String reportName, String holderType) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://datacenter-web.eastmoney.com/api/data/v1/get")
                .queryParam("reportName", reportName)
                .queryParam("columns", "ALL")
                .queryParam("filter", "(HOLDER_NAME=\"" + personName + "\")")
                .queryParam("pageNumber", 1)
                .queryParam("pageSize", 500)
                .queryParam("sortColumns", "END_DATE,HOLDER_RANK")
                .queryParam("sortTypes", "-1,1")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        JsonNode root = getJson(uri, "https://data.eastmoney.com/");
        if (!root.path("success").asBoolean(false)) {
            return List.of();
        }
        JsonNode data = root.path("result").path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<PersonHolding> holdings = new ArrayList<>();
        for (JsonNode row : data) {
            String secucode = text(row, "SECUCODE");
            String companyName = text(row, "SECURITY_NAME_ABBR");
            if (companyName == null) {
                companyName = text(row, "SECURITY_FULL_NAME");
            }
            if (companyName == null) {
                companyName = text(row, "ORG_NAME");
            }
            LocalDate reportDate = parseDate(text(row, "END_DATE"));
            holdings.add(PersonHolding.builder()
                    .companyName(companyName)
                    .creditCode(secucode)
                    .shareholderName(text(row, "HOLDER_NAME"))
                    .shareholderType(holderType)
                    .holdingAmount(decimal(row, "HOLD_NUM"))
                    .holdingRatio(percentToDecimal(firstDecimal(row,
                            "HOLD_NUM_RATIO", "HOLD_RATIO", "FREE_HOLDNUM_RATIO")))
                    .currency("股")
                    .dataSource(SOURCE + ":" + reportName)
                    .sourceUpdatedAt(reportDate)
                    .build());
        }
        return holdings;
    }

    @Override
    public List<ShareholderHolding> getIndustrialRegistryHoldings(CompanyCandidate company) {
        return List.of();
    }

    @Override
    public List<ShareholderHolding> getListedCompanyHoldings(CompanyCandidate company) {
        String secucode = normalizeSecucode(company.getStockCode() != null
                ? company.getStockCode()
                : company.getCreditCode());
        if (secucode == null) {
            return List.of();
        }
        List<ShareholderHolding> holdings = new ArrayList<>();
        holdings.addAll(fetchShareholders(secucode, "RPT_F10_EH_HOLDERS", "十大股东"));
        holdings.addAll(fetchShareholders(secucode, "RPT_F10_EH_FREEHOLDERS", "十大流通股东"));
        return holdings;
    }

    private List<ShareholderHolding> fetchShareholders(String secucode, String reportName, String holderType) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://datacenter-web.eastmoney.com/api/data/v1/get")
                .queryParam("reportName", reportName)
                .queryParam("columns", "ALL")
                .queryParam("filter", "(SECUCODE=\"" + secucode + "\")")
                .queryParam("pageNumber", 1)
                .queryParam("pageSize", 500)
                .queryParam("sortColumns", "END_DATE,HOLDER_RANK")
                .queryParam("sortTypes", "-1,1")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        JsonNode root = getJson(uri, "https://data.eastmoney.com/");
        if (!root.path("success").asBoolean(false)) {
            throw new IllegalStateException("Eastmoney report failed: " + root.path("message").asText());
        }
        JsonNode data = root.path("result").path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<ShareholderHolding> holdings = new ArrayList<>();
        for (JsonNode row : data) {
            LocalDate reportDate = parseDate(text(row, "END_DATE"));
            holdings.add(ShareholderHolding.builder()
                    .shareholderName(text(row, "HOLDER_NAME"))
                    .shareholderType(holderType)
                    .listedHoldingAmount(decimal(row, "HOLD_NUM"))
                    .listedHoldingRatio(percentToDecimal(firstDecimal(row,
                            "HOLD_NUM_RATIO", "HOLD_RATIO", "FREE_HOLDNUM_RATIO")))
                    .currency("股")
                    .source(SOURCE + ":" + reportName)
                    .sourceUpdatedAt(reportDate)
                    .build());
        }
        return holdings;
    }

    private JsonNode getJson(URI uri, String referer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        headers.set(HttpHeaders.REFERER, referer);
        String body = restTemplate.exchange(RequestEntity.get(uri).headers(headers).build(), String.class)
                .getBody();
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Eastmoney JSON response", ex);
        }
    }

    private String toSecucode(String quoteId, String code) {
        if (quoteId == null || code == null) {
            return null;
        }
        if (quoteId.startsWith("1.")) {
            return code + ".SH";
        }
        if (quoteId.startsWith("0.")) {
            return code + ".SZ";
        }
        if (quoteId.startsWith("116.")) {
            return code + ".BJ";
        }
        return null;
    }

    private String normalizeSecucode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.matches("\\d{6}\\.(SH|SZ|BJ)")) {
            return normalized;
        }
        if (normalized.matches("(SH|SZ|BJ)\\d{6}")) {
            return normalized.substring(2) + "." + normalized.substring(0, 2);
        }
        if (normalized.matches("\\d{6}")) {
            if (normalized.startsWith("6")) {
                return normalized + ".SH";
            }
            if (normalized.startsWith("0") || normalized.startsWith("3")) {
                return normalized + ".SZ";
            }
            if (normalized.startsWith("4") || normalized.startsWith("8")) {
                return normalized + ".BJ";
            }
        }
        return normalized;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.decimalValue();
    }

    private BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal value = decimal(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal percentToDecimal(BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        return percent.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_TIME_FORMATTER);
        } catch (RuntimeException ex) {
            return LocalDate.parse(value.substring(0, 10));
        }
    }
}
