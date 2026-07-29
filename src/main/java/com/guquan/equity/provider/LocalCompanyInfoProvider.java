package com.guquan.equity.provider;

import com.guquan.equity.api.CompanyInfoProvider;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.repository.CompanyProfileCacheEntity;
import com.guquan.equity.repository.CompanyProfileCacheRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Order(0)
@Component
public class LocalCompanyInfoProvider implements CompanyInfoProvider {

    public static final String SOURCE = "LOCAL_CACHE";

    private final CompanyProfileCacheRepository repository;

    public LocalCompanyInfoProvider(CompanyProfileCacheRepository repository) {
        this.repository = repository;
    }

    @Override
    public String providerName() {
        return SOURCE;
    }

    @Override
    public List<CompanyProfile> searchByName(String companyName) {
        if (!StringUtils.hasText(companyName)) {
            return List.of();
        }
        return repository.findTop20ByCompanyNameContaining(companyName.trim())
                .stream()
                .map(this::toProfile)
                .toList();
    }

    @Override
    public Optional<CompanyProfile> getByCreditCode(String creditCode) {
        if (!StringUtils.hasText(creditCode)) {
            return Optional.empty();
        }
        return repository.findByCreditCodeIgnoreCase(creditCode.trim())
                .map(this::toProfile);
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
                .source(entity.getSource() == null ? SOURCE : entity.getSource())
                .sourceUpdatedAt(entity.getSourceUpdatedAt())
                .build();
    }
}
