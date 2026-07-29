package com.guquan.equity.service;

import com.guquan.equity.api.CompanyInfoProvider;
import com.guquan.equity.api.CompanyLookupService;
import com.guquan.equity.model.CompanyLookupRequest;
import com.guquan.equity.model.CompanyLookupResult;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.QueryStatus;
import com.guquan.equity.provider.LocalCompanyInfoProvider;
import com.guquan.equity.util.UnifiedSocialCreditCodeUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultCompanyLookupService implements CompanyLookupService {

    private final List<CompanyInfoProvider> providers;
    private final Map<String, CompanyInfoProvider> providersByName;

    public DefaultCompanyLookupService(List<CompanyInfoProvider> providers) {
        this.providers = providers;
        this.providersByName = providers.stream().collect(Collectors.toMap(
                provider -> normalizeProviderName(provider.providerName()),
                Function.identity(),
                (first, ignored) -> first));
    }

    @Override
    public CompanyLookupResult lookup(CompanyLookupRequest request) {
        String queryId = UUID.randomUUID().toString().replace("-", "");
        try {
            return doLookup(queryId, request);
        } catch (RuntimeException ex) {
            return CompanyLookupResult.builder()
                    .queryId(queryId)
                    .status(QueryStatus.DATA_SOURCE_ERROR)
                    .confidence("error")
                    .message(ex.getMessage())
                    .companyCandidates(List.of())
                    .build();
        }
    }

    private CompanyLookupResult doLookup(String queryId, CompanyLookupRequest request) {
        if (request == null || (!StringUtils.hasText(request.getCompanyName())
                && !StringUtils.hasText(request.getCreditCode()))) {
            return baseResult(queryId, QueryStatus.INVALID_REQUEST, "invalid",
                    "companyName or creditCode is required");
        }
        if (StringUtils.hasText(request.getCreditCode())
                && !UnifiedSocialCreditCodeUtil.parse(request.getCreditCode()).isValid()) {
            return baseResult(queryId, QueryStatus.INVALID_REQUEST, "invalid",
                    "统一社会信用代码校验不通过");
        }

        List<CompanyInfoProvider> selectedProviders = selectProviders(request.getDataSource());
        if (selectedProviders.isEmpty()) {
            return baseResult(queryId, QueryStatus.INVALID_REQUEST, "invalid",
                    "Unsupported dataSource: " + request.getDataSource());
        }

        List<CompanyProfile> candidates = selectedProviders.stream()
                .flatMap(provider -> lookupWithProvider(provider, request).stream())
                .filter(profile -> profile.getCompanyName() != null || profile.getCreditCode() != null)
                .collect(Collectors.toMap(this::companyKey, Function.identity(), this::mergeProfiles,
                        LinkedHashMap::new))
                .values().stream().toList();

        if (candidates.isEmpty()) {
            return baseResult(queryId, QueryStatus.NOT_FOUND, "not_found",
                    "No company matched the given name or creditCode.");
        }

        Optional<CompanyProfile> exact = findExact(candidates, request);
        if (exact.isPresent()) {
            return found(queryId, exact.get(), "exact");
        }
        if (candidates.size() > 1) {
            return CompanyLookupResult.builder()
                    .queryId(queryId)
                    .companyCandidates(candidates)
                    .status(QueryStatus.COMPANY_AMBIGUOUS)
                    .confidence("unconfirmed")
                    .message("Multiple companies matched the given query.")
                    .build();
        }
        return found(queryId, candidates.get(0), "likely");
    }

    private CompanyLookupResult found(String queryId, CompanyProfile profile, String confidence) {
        return CompanyLookupResult.builder()
                .queryId(queryId)
                .company(profile)
                .companyCandidates(List.of(profile))
                .status(QueryStatus.FOUND)
                .confidence(confidence)
                .message("Company profile matched.")
                .build();
    }

    private List<CompanyInfoProvider> selectProviders(String dataSource) {
        if ("all".equalsIgnoreCase(dataSource)) {
            return providers;
        }
        if (!StringUtils.hasText(dataSource) || "auto".equalsIgnoreCase(dataSource)) {
            CompanyInfoProvider local = providersByName.get(LocalCompanyInfoProvider.SOURCE);
            return local == null ? providers : List.of(local);
        }
        CompanyInfoProvider provider = providersByName.get(normalizeProviderAlias(dataSource));
        return provider == null ? List.of() : List.of(provider);
    }

    private List<CompanyProfile> lookupWithProvider(
            CompanyInfoProvider provider, CompanyLookupRequest request) {
        if (StringUtils.hasText(request.getCreditCode())) {
            Optional<CompanyProfile> byCode = provider.getByCreditCode(request.getCreditCode());
            if (byCode.isPresent()) {
                return List.of(byCode.get());
            }
        }
        return StringUtils.hasText(request.getCompanyName())
                ? provider.searchByName(request.getCompanyName()) : List.of();
    }

    private Optional<CompanyProfile> findExact(
            List<CompanyProfile> candidates, CompanyLookupRequest request) {
        if (StringUtils.hasText(request.getCreditCode())) {
            String code = request.getCreditCode().trim();
            Optional<CompanyProfile> byCode = candidates.stream()
                    .filter(profile -> code.equalsIgnoreCase(profile.getCreditCode())).findFirst();
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        if (StringUtils.hasText(request.getCompanyName())) {
            String name = request.getCompanyName().trim();
            return candidates.stream().filter(profile -> name.equals(profile.getCompanyName())).findFirst();
        }
        return Optional.empty();
    }

    private CompanyLookupResult baseResult(
            String queryId, QueryStatus status, String confidence, String message) {
        return CompanyLookupResult.builder()
                .queryId(queryId)
                .companyCandidates(List.of())
                .status(status)
                .confidence(confidence)
                .message(message)
                .build();
    }

    private String companyKey(CompanyProfile profile) {
        return StringUtils.hasText(profile.getCreditCode())
                ? profile.getCreditCode().trim().toUpperCase(Locale.ROOT)
                : String.valueOf(profile.getCompanyName()).trim();
    }

    private CompanyProfile mergeProfiles(CompanyProfile first, CompanyProfile second) {
        return CompanyProfile.builder()
                .companyName(firstText(first.getCompanyName(), second.getCompanyName()))
                .creditCode(firstText(first.getCreditCode(), second.getCreditCode()))
                .legalPerson(firstText(first.getLegalPerson(), second.getLegalPerson()))
                .registrationStatus(firstText(first.getRegistrationStatus(), second.getRegistrationStatus()))
                .industryName(firstText(first.getIndustryName(), second.getIndustryName()))
                .industryCode(firstText(first.getIndustryCode(), second.getIndustryCode()))
                .entityType(firstText(first.getEntityType(), second.getEntityType()))
                .classificationSource(firstText(first.getClassificationSource(), second.getClassificationSource()))
                .classificationConfidence(firstText(first.getClassificationConfidence(), second.getClassificationConfidence()))
                .registrationAuthority(firstText(first.getRegistrationAuthority(), second.getRegistrationAuthority()))
                .registrationAuthorityCode(firstText(first.getRegistrationAuthorityCode(), second.getRegistrationAuthorityCode()))
                .province(firstText(first.getProvince(), second.getProvince()))
                .city(firstText(first.getCity(), second.getCity()))
                .district(firstText(first.getDistrict(), second.getDistrict()))
                .provinceCode(firstText(first.getProvinceCode(), second.getProvinceCode()))
                .cityCode(firstText(first.getCityCode(), second.getCityCode()))
                .districtCode(firstText(first.getDistrictCode(), second.getDistrictCode()))
                .areaCode(firstText(first.getAreaCode(), second.getAreaCode()))
                .registeredAddressAreaCode(firstText(first.getRegisteredAddressAreaCode(), second.getRegisteredAddressAreaCode()))
                .areaCodeSource(firstText(first.getAreaCodeSource(), second.getAreaCodeSource()))
                .registeredAddress(firstText(first.getRegisteredAddress(), second.getRegisteredAddress()))
                .source(firstText(first.getSource(), second.getSource()))
                .sourceUpdatedAt(first.getSourceUpdatedAt() != null
                        ? first.getSourceUpdatedAt() : second.getSourceUpdatedAt())
                .build();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String normalizeProviderName(String providerName) {
        return providerName == null ? ""
                : providerName.trim().replace("-", "_").toUpperCase(Locale.ROOT);
    }

    private String normalizeProviderAlias(String providerName) {
        return switch (normalizeProviderName(providerName)) {
            case "LOCAL" -> LocalCompanyInfoProvider.SOURCE;
            default -> normalizeProviderName(providerName);
        };
    }
}
