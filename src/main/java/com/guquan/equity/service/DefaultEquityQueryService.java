package com.guquan.equity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guquan.equity.api.EquityDataProvider;
import com.guquan.equity.api.EquityQueryService;
import com.guquan.equity.model.CompanyCandidate;
import com.guquan.equity.model.EquityQueryRequest;
import com.guquan.equity.model.EquityQueryResult;
import com.guquan.equity.model.PersonHolding;
import com.guquan.equity.model.PersonSearchRequest;
import com.guquan.equity.model.PersonSearchResult;
import com.guquan.equity.model.QueryStatus;
import com.guquan.equity.model.ShareholderHolding;
import com.guquan.equity.repository.EquityQueryRecordEntity;
import com.guquan.equity.repository.EquityQueryRecordRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultEquityQueryService implements EquityQueryService {

    private final EquityDataProvider dataProvider;
    private final Map<String, EquityDataProvider> allProviders;
    private final EquityQueryRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    public DefaultEquityQueryService(
            EquityDataProvider dataProvider,
            List<EquityDataProvider> allProviders,
            EquityQueryRecordRepository recordRepository,
            ObjectMapper objectMapper) {
        this.dataProvider = dataProvider;
        this.allProviders = allProviders.stream()
                .collect(Collectors.toMap(
                    EquityDataProvider::providerName,
                    Function.identity(),
                    (a, b) -> a  // dup -> keep first
                ));
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public EquityQueryResult query(EquityQueryRequest request) {
        String queryId = UUID.randomUUID().toString().replace("-", "");
        EquityQueryResult result;
        try {
            result = doQuery(queryId, request);
        } catch (RuntimeException ex) {
            result = EquityQueryResult.builder()
                    .queryId(queryId)
                    .personName(request == null ? null : request.getPersonName())
                    .status(QueryStatus.DATA_SOURCE_ERROR)
                    .confidence("error")
                    .message(ex.getMessage())
                    .holdings(List.of())
                    .companyCandidates(List.of())
                    .build();
        }
        saveRecord(request, result);
        return result;
    }

    private EquityQueryResult doQuery(String queryId, EquityQueryRequest request) {
        if (request == null || !StringUtils.hasText(request.getPersonName())) {
            return baseResult(queryId, request, QueryStatus.INVALID_REQUEST, "invalid",
                    "personName is required");
        }
        CompanyMatch companyMatch = matchCompany(request);
        if (companyMatch.status != QueryStatus.FOUND) {
            return EquityQueryResult.builder()
                    .queryId(queryId)
                    .personName(request.getPersonName())
                    .companyCandidates(companyMatch.candidates)
                    .holdings(List.of())
                    .status(companyMatch.status)
                    .confidence("unconfirmed")
                    .message(companyMatch.message)
                    .build();
        }

        CompanyCandidate company = companyMatch.company;
        List<ShareholderHolding> holdings = new ArrayList<>();
        holdings.addAll(dataProvider.getIndustrialRegistryHoldings(company));
        holdings.addAll(dataProvider.getListedCompanyHoldings(company));

        String personName = request.getPersonName().trim();
        List<ShareholderHolding> matchedHoldings = holdings.stream()
                .filter(holding -> holderNameMatches(personName, holding.getShareholderName()))
                .toList();

        if (matchedHoldings.isEmpty()) {
            return EquityQueryResult.builder()
                    .queryId(queryId)
                    .personName(personName)
                    .company(company)
                    .companyCandidates(List.of(company))
                    .holdings(List.of())
                    .status(QueryStatus.NOT_FOUND)
                    .confidence("not_found")
                    .message("No matching holder was found in the public top shareholder lists. "
                            + "This does not prove the person holds no shares.")
                    .build();
        }

        return EquityQueryResult.builder()
                .queryId(queryId)
                .personName(personName)
                .company(company)
                .companyCandidates(List.of(company))
                .holdings(matchedHoldings)
                .status(QueryStatus.FOUND)
                .confidence(StringUtils.hasText(request.getCreditCode()) ? "confirmed_company" : "likely")
                .message("Matched shareholder holdings.")
                .build();
    }

    private CompanyMatch matchCompany(EquityQueryRequest request) {
        if (StringUtils.hasText(request.getCreditCode())) {
            return dataProvider.getCompanyByCreditCode(request.getCreditCode())
                    .map(company -> CompanyMatch.found(company))
                    .orElseGet(() -> CompanyMatch.notFound("No company matched the given creditCode."));
        }
        if (!StringUtils.hasText(request.getCompanyName())) {
            return CompanyMatch.invalid("creditCode or companyName is required.");
        }
        List<CompanyCandidate> candidates = dataProvider.searchCompany(request.getCompanyName());
        if (candidates.isEmpty()) {
            return CompanyMatch.notFound("No company matched the given companyName.");
        }
        if (candidates.size() > 1) {
            return CompanyMatch.ambiguous(candidates);
        }
        return CompanyMatch.found(candidates.get(0));
    }

    @Override
    public PersonSearchResult searchPerson(PersonSearchRequest request) {
        String queryId = UUID.randomUUID().toString().replace("-", "");
        try {
            return doSearchPerson(queryId, request);
        } catch (RuntimeException ex) {
            return PersonSearchResult.builder()
                    .queryId(queryId)
                    .personName(request == null ? null : request.getPersonName())
                    .phoneNumber(request == null ? null : request.getPhoneNumber())
                    .idNumber(request == null ? null : request.getIdNumber())
                    .status(QueryStatus.DATA_SOURCE_ERROR)
                    .confidence("error")
                    .message(ex.getMessage())
                    .holdings(List.of())
                    .build();
        }
    }

    private PersonSearchResult doSearchPerson(String queryId, PersonSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.getPersonName())) {
            return PersonSearchResult.builder()
                    .queryId(queryId)
                    .personName(request == null ? null : request.getPersonName())
                    .status(QueryStatus.INVALID_REQUEST)
                    .confidence("invalid")
                    .message("personName is required")
                    .holdings(List.of())
                    .build();
        }
        String personName = request.getPersonName().trim();

        // 根据 dataSource 选择数据源
        String ds = request.getDataSource();
        List<PersonHolding> holdings;
        String dataSourceUsed;
        if (ds == null || ds.isBlank() || "auto".equalsIgnoreCase(ds) || "eastmoney".equalsIgnoreCase(ds)) {
            // 默认：东方财富
            holdings = dataProvider.searchPersonHoldings(personName);
            dataSourceUsed = dataProvider.providerName();
        } else if ("qichacha".equalsIgnoreCase(ds)) {
            // 企查查
            EquityDataProvider qcc = allProviders.get("QICHACHA");
            if (qcc != null) {
                holdings = qcc.searchPersonHoldings(personName);
                dataSourceUsed = qcc.providerName();
            } else {
                return PersonSearchResult.builder()
                        .queryId(queryId).personName(personName)
                        .status(QueryStatus.DATA_SOURCE_ERROR).confidence("error")
                        .message("企查查数据源未启用，请在 application.yml 中配置 qichacha.app-key")
                        .holdings(List.of()).build();
            }
        } else if ("all".equalsIgnoreCase(ds)) {
            // 合并多个数据源
            holdings = new ArrayList<>();
            dataSourceUsed = "MULTI";
            for (EquityDataProvider p : this.allProviders.values()) {
                try {
                    List<PersonHolding> ph = p.searchPersonHoldings(personName);
                    if (ph != null && !ph.isEmpty()) {
                        holdings.addAll(ph);
                    }
                } catch (RuntimeException ignored) {
                    // 单个数据源失败不阻断
                }
            }
        } else {
            // 非法 dataSource
            return PersonSearchResult.builder()
                    .queryId(queryId).personName(personName)
                    .status(QueryStatus.INVALID_REQUEST).confidence("invalid")
                    .message("不支持的数据源: " + ds)
                    .holdings(List.of()).build();
        }

        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            String companyFilter = request.getCompanyName().trim();
            holdings = holdings.stream()
                    .filter(h -> h.getCompanyName() != null && h.getCompanyName().contains(companyFilter))
                    .toList();
        }
        savePersonRecord(request, queryId, holdings, dataSourceUsed);
        if (holdings.isEmpty()) {
            return PersonSearchResult.builder()
                    .queryId(queryId)
                    .personName(personName)
                    .phoneNumber(request.getPhoneNumber())
                    .idNumber(request.getIdNumber())
                    .companyName(request.getCompanyName())
                    .holdings(List.of())
                    .status(QueryStatus.NOT_FOUND)
                    .confidence("not_found")
                    .message("在所选数据源 (" + dataSourceUsed + ") 中未找到该人的持股记录。")
                    .build();
        }
        return PersonSearchResult.builder()
                .queryId(queryId)
                .personName(personName)
                .phoneNumber(request.getPhoneNumber())
                .idNumber(request.getIdNumber())
                .companyName(request.getCompanyName())
                .holdings(holdings)
                .status(QueryStatus.FOUND)
                .confidence("likely")
                .message("找到匹配记录。数据来源: " + dataSourceUsed)
                .build();
    }

    private void savePersonRecord(PersonSearchRequest request, String queryId, List<PersonHolding> holdings, String ds) {
        EquityQueryRecordEntity record = new EquityQueryRecordEntity();
        record.setQueryId(queryId);
        record.setPersonName(request == null ? "" : request.getPersonName());
        record.setPhoneNumber(request == null ? null : request.getPhoneNumber());
        record.setIdNumber(request == null ? null : request.getIdNumber());
        record.setCompanyName(request == null ? null : request.getCompanyName());
        record.setStatus(holdings.isEmpty() ? QueryStatus.NOT_FOUND : QueryStatus.FOUND);
        record.setConfidence(holdings.isEmpty() ? "not_found" : "likely");
        record.setResultJson(toJson(holdings));
        record.setDataSource(ds != null ? ds : dataProvider.providerName());
        record.setOperatorId(request == null ? null : request.getOperatorId());
        record.setQueryReason(request == null ? null : request.getQueryReason());
        record.setCreatedAt(LocalDateTime.now());
        recordRepository.save(record);
    }

    private EquityQueryResult baseResult(
            String queryId,
            EquityQueryRequest request,
            QueryStatus status,
            String confidence,
            String message) {
        return EquityQueryResult.builder()
                .queryId(queryId)
                .personName(request == null ? null : request.getPersonName())
                .holdings(List.of())
                .companyCandidates(List.of())
                .status(status)
                .confidence(confidence)
                .message(message)
                .build();
    }

    private void saveRecord(EquityQueryRequest request, EquityQueryResult result) {
        EquityQueryRecordEntity record = new EquityQueryRecordEntity();
        record.setQueryId(result.getQueryId());
        record.setPersonName(result.getPersonName() == null ? "" : result.getPersonName());
        record.setCompanyName(request == null ? null : request.getCompanyName());
        record.setCreditCode(request == null ? null : request.getCreditCode());
        if (result.getCompany() != null) {
            record.setMatchedCompanyName(result.getCompany().getCompanyName());
            record.setMatchedCreditCode(result.getCompany().getCreditCode());
        }
        record.setStatus(result.getStatus());
        record.setConfidence(result.getConfidence());
        record.setResultJson(toJson(result));
        record.setDataSource(dataProvider.providerName());
        record.setOperatorId(request == null ? null : request.getOperatorId());
        record.setQueryReason(request == null ? null : request.getQueryReason());
        record.setCreatedAt(LocalDateTime.now());
        recordRepository.save(record);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean holderNameMatches(String queryName, String holderName) {
        String normalizedQuery = normalizeHolderName(queryName);
        String normalizedHolder = normalizeHolderName(holderName);
        if (!StringUtils.hasText(normalizedQuery) || !StringUtils.hasText(normalizedHolder)) {
            return false;
        }
        if (Objects.equals(normalizedQuery, normalizedHolder)) {
            return true;
        }
        return normalizedQuery.length() >= 4
                && normalizedHolder.length() >= 4
                && (normalizedHolder.contains(normalizedQuery) || normalizedQuery.contains(normalizedHolder));
    }

    private String normalizeHolderName(String value) {
        if (value == null) {
            return null;
        }
        return value.trim()
                .replace(" ", "")
                .replace("　", "")
                .replace("（", "(")
                .replace("）", ")")
                .replace("有限责任公司", "有限公司")
                .replace("股份有限公司", "股份公司");
    }

    private record CompanyMatch(
            QueryStatus status,
            CompanyCandidate company,
            List<CompanyCandidate> candidates,
            String message) {

        private static CompanyMatch found(CompanyCandidate company) {
            return new CompanyMatch(QueryStatus.FOUND, company, List.of(company), "Company matched.");
        }

        private static CompanyMatch notFound(String message) {
            return new CompanyMatch(QueryStatus.NOT_FOUND, null, List.of(), message);
        }

        private static CompanyMatch invalid(String message) {
            return new CompanyMatch(QueryStatus.INVALID_REQUEST, null, List.of(), message);
        }

        private static CompanyMatch ambiguous(List<CompanyCandidate> candidates) {
            return new CompanyMatch(QueryStatus.COMPANY_AMBIGUOUS, null, candidates,
                    "Multiple companies matched the given companyName.");
        }
    }
}
