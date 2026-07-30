package com.guquan.equity.service;

import com.guquan.equity.api.CompanyProfileCacheService;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.CreditCodeParseResult;
import com.guquan.equity.provider.LocalCompanyInfoProvider;
import com.guquan.equity.repository.CompanyProfileCacheEntity;
import com.guquan.equity.repository.CompanyProfileCacheRepository;
import com.guquan.equity.repository.CompanyProfileAliasEntity;
import com.guquan.equity.repository.CompanyProfileAliasRepository;
import com.guquan.equity.util.CompanyNameNormalizer;
import com.guquan.equity.util.UnifiedSocialCreditCodeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultCompanyProfileCacheService implements CompanyProfileCacheService {

    private final CompanyProfileCacheRepository repository;
    private final CompanyProfileAliasRepository aliasRepository;

    public DefaultCompanyProfileCacheService(
            CompanyProfileCacheRepository repository,
            CompanyProfileAliasRepository aliasRepository) {
        this.repository = repository;
        this.aliasRepository = aliasRepository;
    }

    @Override
    @Transactional
    public CompanyProfile save(CompanyProfile profile) {
        validate(profile);
        String companyName = profile.getCompanyName().trim();
        String creditCode = normalizeCreditCode(profile.getCreditCode());
        CompanyProfileCacheEntity entity = creditCode == null
                ? repository.findFirstByCompanyNameIgnoreCase(companyName).orElse(null)
                : repository.findByCreditCodeIgnoreCase(creditCode).orElse(null);
        if (entity == null) {
            entity = new CompanyProfileCacheEntity();
            entity.setCreatedAt(LocalDateTime.now());
        }
        mergeEntity(entity, profile, creditCode);
        entity.setUpdatedAt(LocalDateTime.now());
        return toProfile(repository.save(entity));
    }

    @Override
    @Transactional
    public List<CompanyProfile> saveAll(List<CompanyProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        for (int i = 0; i < profiles.size(); i++) {
            try {
                validate(profiles.get(i));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 条档案无效：" + ex.getMessage(), ex);
            }
        }
        List<CompanyProfile> results = new ArrayList<>(profiles.size());
        for (CompanyProfile profile : profiles) {
            results.add(save(profile));
        }
        return results;
    }

    @Override
    public CreditCodeParseResult parseCreditCode(String creditCode) {
        return UnifiedSocialCreditCodeUtil.parse(creditCode);
    }

    @Override
    public Optional<CompanyProfile> findExact(String companyName) {
        if (!StringUtils.hasText(companyName)) {
            return Optional.empty();
        }
        Optional<CompanyProfileCacheEntity> exact = repository.findFirstByCompanyNameIgnoreCase(companyName.trim());
        if (exact.isPresent()) {
            return exact.map(this::toProfile);
        }
        Optional<CompanyProfileCacheEntity> normalized = repository.findFirstByNormalizedCompanyName(
                CompanyNameNormalizer.normalize(companyName));
        if (normalized.isPresent()) {
            return normalized.map(this::toProfile);
        }
        return aliasRepository.findByNormalizedAlias(CompanyNameNormalizer.normalize(companyName))
                .flatMap(alias -> StringUtils.hasText(alias.getCreditCode())
                        ? repository.findByCreditCodeIgnoreCase(alias.getCreditCode())
                        : repository.findFirstByCompanyNameIgnoreCase(alias.getCompanyName()))
                .map(this::toProfile);
    }

    @Override
    @Transactional
    public void saveAlias(String aliasName, CompanyProfile profile) {
        if (!StringUtils.hasText(aliasName) || profile == null || !StringUtils.hasText(profile.getCompanyName())) {
            return;
        }
        String normalized = CompanyNameNormalizer.normalize(aliasName);
        if (normalized.isEmpty() || normalized.equals(CompanyNameNormalizer.normalize(profile.getCompanyName()))) {
            return;
        }
        CompanyProfileAliasEntity alias = aliasRepository.findByNormalizedAlias(normalized)
                .orElseGet(CompanyProfileAliasEntity::new);
        LocalDateTime now = LocalDateTime.now();
        if (alias.getId() == null) {
            alias.setCreatedAt(now);
        }
        alias.setAliasName(aliasName.trim());
        alias.setNormalizedAlias(normalized);
        alias.setCompanyName(profile.getCompanyName().trim());
        alias.setCreditCode(normalizeCreditCode(profile.getCreditCode()));
        alias.setUpdatedAt(now);
        aliasRepository.save(alias);
    }

    private void validate(CompanyProfile profile) {
        if (profile == null || !StringUtils.hasText(profile.getCompanyName())) {
            throw new IllegalArgumentException("公司/单位名称不能为空");
        }
        validateLength("公司/单位名称", profile.getCompanyName(), 255);
        if (StringUtils.hasText(profile.getCreditCode())
                && !UnifiedSocialCreditCodeUtil.parse(profile.getCreditCode()).isValid()) {
            throw new IllegalArgumentException("统一社会信用代码校验不通过");
        }
        validateLength("法定代表人", profile.getLegalPerson(), 100);
        validateLength("经营状态", profile.getRegistrationStatus(), 50);
        validateLength("行业", profile.getIndustryName(), 1000);
        validateLength("行业代码", profile.getIndustryCode(), 50);
        validateLength("企业类型", profile.getEntityType(), 255);
        validateLength("分类来源", profile.getClassificationSource(), 50);
        validateLength("分类置信度", profile.getClassificationConfidence(), 20);
        validateLength("登记机关", profile.getRegistrationAuthority(), 255);
        validateLength("登记机关代码", profile.getRegistrationAuthorityCode(), 12);
        validateLength("省", profile.getProvince(), 50);
        validateLength("市", profile.getCity(), 50);
        validateLength("区县", profile.getDistrict(), 50);
        validateLength("注册地址", profile.getRegisteredAddress(), 500);
        validateLength("数据来源", profile.getSource(), 100);
        validateAreaCode(profile.getRegisteredAddressAreaCode());
        validateAreaCode(profile.getAreaCode());
    }

    private void validateLength(String field, String value, int maxLength) {
        if (StringUtils.hasText(value) && value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + "内容异常，超过 " + maxLength
                    + " 个字符；请检查官网原文是否发生字段错位");
        }
    }

    private void validateAreaCode(String code) {
        if (StringUtils.hasText(code) && !code.trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("注册地址行政区划代码必须为 6 位数字");
        }
    }

    private void mergeEntity(CompanyProfileCacheEntity entity, CompanyProfile profile, String creditCode) {
        setIfText(profile.getCompanyName(), entity::setCompanyName);
        entity.setNormalizedCompanyName(CompanyNameNormalizer.normalize(profile.getCompanyName()));
        if (creditCode != null) {
            entity.setCreditCode(creditCode);
            if (!StringUtils.hasText(profile.getRegistrationAuthorityCode())) {
                entity.setRegistrationAuthorityCode(UnifiedSocialCreditCodeUtil.registrationAuthorityCode(creditCode));
            }
        }
        setIfText(profile.getLegalPerson(), entity::setLegalPerson);
        setIfText(profile.getRegistrationStatus(), entity::setRegistrationStatus);
        setIfText(profile.getIndustryName(), entity::setIndustryName);
        setIfText(profile.getIndustryCode(), entity::setIndustryCode);
        setIfText(profile.getEntityType(), entity::setEntityType);
        setIfText(profile.getClassificationSource(), entity::setClassificationSource);
        setIfText(profile.getClassificationConfidence(), entity::setClassificationConfidence);
        setIfText(profile.getRegistrationAuthority(), entity::setRegistrationAuthority);
        setIfText(profile.getRegistrationAuthorityCode(), entity::setRegistrationAuthorityCode);
        setIfText(profile.getProvince(), entity::setProvince);
        setIfText(profile.getCity(), entity::setCity);
        setIfText(profile.getDistrict(), entity::setDistrict);
        setIfText(profile.getProvinceCode(), entity::setProvinceCode);
        setIfText(profile.getCityCode(), entity::setCityCode);
        setIfText(profile.getDistrictCode(), entity::setDistrictCode);

        String addressCode = firstText(profile.getRegisteredAddressAreaCode(), profile.getAreaCode());
        if (addressCode != null) {
            entity.setRegisteredAddressAreaCode(addressCode);
            entity.setAreaCode(addressCode);
            entity.setAreaCodeSource(StringUtils.hasText(profile.getAreaCodeSource())
                    ? profile.getAreaCodeSource().trim() : "ADDRESS");
        }
        setIfText(profile.getRegisteredAddress(), entity::setRegisteredAddress);
        setIfText(profile.getSource(), entity::setSource);
        if (!StringUtils.hasText(entity.getSource())) {
            entity.setSource(LocalCompanyInfoProvider.SOURCE);
        }
        if (profile.getSourceUpdatedAt() != null) {
            entity.setSourceUpdatedAt(profile.getSourceUpdatedAt());
        } else if (entity.getSourceUpdatedAt() == null) {
            entity.setSourceUpdatedAt(LocalDate.now());
        }
        if (!StringUtils.hasText(entity.getAreaCodeSource())) {
            entity.setAreaCodeSource("UNKNOWN");
        }
    }

    private String normalizeCreditCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }

    private void setIfText(String value, Consumer<String> setter) {
        if (StringUtils.hasText(value)) {
            setter.accept(value.trim());
        }
    }

    private CompanyProfile toProfile(CompanyProfileCacheEntity entity) {
        return CompanyProfile.builder()
                .companyName(entity.getCompanyName())
                .creditCode(entity.getCreditCode())
                .legalPerson(entity.getLegalPerson())
                .registrationStatus(entity.getRegistrationStatus())
                .industryName(entity.getIndustryName())
                .industryCode(entity.getIndustryCode())
                .entityType(entity.getEntityType())
                .classificationSource(entity.getClassificationSource())
                .classificationConfidence(entity.getClassificationConfidence())
                .registrationAuthority(entity.getRegistrationAuthority())
                .registrationAuthorityCode(entity.getRegistrationAuthorityCode())
                .province(entity.getProvince())
                .city(entity.getCity())
                .district(entity.getDistrict())
                .provinceCode(entity.getProvinceCode())
                .cityCode(entity.getCityCode())
                .districtCode(entity.getDistrictCode())
                .areaCode(entity.getAreaCode())
                .registeredAddressAreaCode(entity.getRegisteredAddressAreaCode())
                .areaCodeSource(entity.getAreaCodeSource())
                .registeredAddress(entity.getRegisteredAddress())
                .source(entity.getSource())
                .sourceUpdatedAt(entity.getSourceUpdatedAt())
                .build();
    }
}
