package com.guquan.equity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.repository.CompanyProfileCacheEntity;
import com.guquan.equity.repository.CompanyProfileAliasRepository;
import com.guquan.equity.repository.CompanyProfileAliasEntity;
import com.guquan.equity.util.CompanyNameNormalizer;
import com.guquan.equity.repository.CompanyProfileCacheRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultCompanyProfileCacheServiceTest {

    @Test
    void partialSaveKeepsExistingFieldsAndStoresAddressCode() {
        CompanyProfileCacheRepository repository = mock(CompanyProfileCacheRepository.class);
        CompanyProfileAliasRepository aliasRepository = mock(CompanyProfileAliasRepository.class);
        CompanyProfileCacheEntity existing = new CompanyProfileCacheEntity();
        existing.setCompanyName("华为技术有限公司");
        existing.setCreditCode("914403001922038216");
        existing.setLegalPerson("任正非");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());
        when(repository.findByCreditCodeIgnoreCase("914403001922038216"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(CompanyProfileCacheEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DefaultCompanyProfileCacheService service = new DefaultCompanyProfileCacheService(repository, aliasRepository);

        CompanyProfile saved = service.save(CompanyProfile.builder()
                .companyName("华为技术有限公司")
                .creditCode("914403001922038216")
                .industryName("软件和信息技术服务业")
                .registeredAddressAreaCode("440307")
                .build());

        assertThat(saved.getLegalPerson()).isEqualTo("任正非");
        assertThat(saved.getIndustryName()).isEqualTo("软件和信息技术服务业");
        assertThat(saved.getRegisteredAddressAreaCode()).isEqualTo("440307");
        assertThat(saved.getRegistrationAuthorityCode()).isEqualTo("440300");
    }

    @Test
    void exactLookupReusesConfirmedAlias() {
        CompanyProfileCacheRepository repository = mock(CompanyProfileCacheRepository.class);
        CompanyProfileAliasRepository aliasRepository = mock(CompanyProfileAliasRepository.class);
        CompanyProfileAliasEntity alias = new CompanyProfileAliasEntity();
        alias.setAliasName("示例科技");
        alias.setNormalizedAlias(CompanyNameNormalizer.normalize("示例科技"));
        alias.setCompanyName("深圳示例科技有限公司");
        alias.setCreditCode("914403001922038216");
        CompanyProfileCacheEntity cached = new CompanyProfileCacheEntity();
        cached.setCompanyName("深圳示例科技有限公司");
        cached.setCreditCode("914403001922038216");
        when(repository.findFirstByCompanyNameIgnoreCase("示例科技")).thenReturn(Optional.empty());
        when(aliasRepository.findByNormalizedAlias(CompanyNameNormalizer.normalize("示例科技")))
                .thenReturn(Optional.of(alias));
        when(repository.findByCreditCodeIgnoreCase("914403001922038216"))
                .thenReturn(Optional.of(cached));

        DefaultCompanyProfileCacheService service =
                new DefaultCompanyProfileCacheService(repository, aliasRepository);

        assertThat(service.findExact("示例科技"))
                .get()
                .extracting(CompanyProfile::getCompanyName)
                .isEqualTo("深圳示例科技有限公司");
    }
}
