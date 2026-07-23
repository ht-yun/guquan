package com.guquan.equity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guquan.equity.api.CompanyProfileCacheService;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.GsxtTextImportRequest;
import com.guquan.equity.service.GsxtPageParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class GsxtTextImportControllerTest {

    @Test
    void previewsAndSavesCopiedGsxtText() {
        CompanyProfileCacheService cacheService = mock(CompanyProfileCacheService.class);
        when(cacheService.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        GsxtTextImportController controller =
                new GsxtTextImportController(new GsxtPageParser(), cacheService);
        GsxtTextImportRequest request = new GsxtTextImportRequest();
        request.setPageText("""
                华为技术有限公司
                统一社会信用代码：914403001922038216
                所属行业：计算机、通信和其他电子设备制造业
                企业类型：有限责任公司
                注册地址行政区划代码：440307
                """);

        var preview = controller.preview(request);
        var saved = controller.save(request);

        assertThat(preview.get("candidateCount")).isEqualTo(1);
        assertThat(saved.get("savedCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<CompanyProfile> profiles = (List<CompanyProfile>) saved.get("profiles");
        assertThat(profiles.get(0).getSource()).isEqualTo("GSXT_MANUAL_IMPORT");
    }
}
