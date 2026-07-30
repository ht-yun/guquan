package com.guquan.equity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guquan.equity.model.CompanyAllSectionsView;
import com.guquan.equity.model.CompanyBatchAutomationTarget;
import com.guquan.equity.model.CompanyBatchCompanyView;
import com.guquan.equity.model.CompanyBatchStatus;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.EdgeExtensionCaptureRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EdgeExtensionBridgeServiceTest {

    private final CompanyBatchService batchService = mock(CompanyBatchService.class);
    private final EdgeExtensionBridgeService service = new EdgeExtensionBridgeService(batchService);

    @Test
    void returnsNextTargetWithValidPairingCode() {
        when(batchService.nextAutomationTarget("job-1")).thenReturn(Optional.of(
                CompanyBatchAutomationTarget.builder()
                        .companyId(12L)
                        .companyName("北京示例科技有限公司")
                        .creditCode("91110000123456789X")
                        .build()));

        var target = service.nextTarget(service.pairing().getPairingCode(), "job-1");

        assertThat(target.getJobId()).isEqualTo("job-1");
        assertThat(target.getCompanyId()).isEqualTo(12L);
        assertThat(target.getCompanyName()).isEqualTo("北京示例科技有限公司");
    }

    @Test
    void rejectsInvalidPairingCode() {
        assertThatThrownBy(() -> service.nextTarget("wrong-code", "job-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配对码无效");
    }

    @Test
    void rejectsCaptureFromNonGsxtPage() {
        EdgeExtensionCaptureRequest request = new EdgeExtensionCaptureRequest();
        request.setPageText("企业名称：北京示例科技有限公司\n统一社会信用代码：91110000123456789X\n".repeat(3));
        request.setCurrentUrl("https://example.com/company");

        assertThatThrownBy(() -> service.capture(
                service.pairing().getPairingCode(), "job-1", 12L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是国家企业信用信息公示系统");
    }

    @Test
    void savesCaptureAndReturnsNextCompany() {
        String pageText = """
                企业名称：北京示例科技有限公司
                统一社会信用代码：91110000123456789X
                法定代表人：张三
                登记状态：存续
                登记机关：北京市市场监督管理局
                经营范围：技术开发、技术服务和技术咨询。
                """;
        EdgeExtensionCaptureRequest request = new EdgeExtensionCaptureRequest();
        request.setPageText(pageText);
        request.setCurrentUrl("https://www.gsxt.gov.cn/example");
        when(batchService.confirmAllSections(eq("job-1"), eq(12L), anyString(), anyString()))
                .thenReturn(CompanyAllSectionsView.builder().build());
        when(batchService.company("job-1", 12L)).thenReturn(
                CompanyBatchCompanyView.builder().companyId(12L).status(CompanyBatchStatus.RESOLVED)
                        .profile(CompanyProfile.builder().companyName("北京示例科技有限公司")
                                .creditCode("91110000123456789X").build())
                        .build());
        when(batchService.nextAutomationTarget("job-1")).thenReturn(Optional.of(
                CompanyBatchAutomationTarget.builder().companyId(13L).companyName("上海示例公司").build()));

        var result = service.capture(service.pairing().getPairingCode(), "job-1", 12L, request);

        assertThat(result.isCompleted()).isTrue();
        assertThat(result.getNextTarget().getCompanyId()).isEqualTo(13L);
        assertThat(result.getMessage()).contains("下一家");
    }

    @Test
    void continuesToNextCompanyWhenBasicProfileWasSavedButSectionsNeedReview() {
        EdgeExtensionCaptureRequest request = new EdgeExtensionCaptureRequest();
        request.setPageText(("企业名称：北京示例科技有限公司\n"
                + "统一社会信用代码：91110000123456789X\n法定代表人：张三\n登记状态：存续\n").repeat(2));
        request.setCurrentUrl("https://www.gsxt.gov.cn/example");
        when(batchService.confirmAllSections(eq("job-1"), eq(12L), anyString(), anyString()))
                .thenReturn(CompanyAllSectionsView.builder().build());
        when(batchService.company("job-1", 12L)).thenReturn(
                CompanyBatchCompanyView.builder().companyId(12L).status(CompanyBatchStatus.NEEDS_REVIEW)
                        .profile(CompanyProfile.builder().companyName("北京示例科技有限公司")
                                .creditCode("91110000123456789X").build())
                        .collectionMessage("基本信息已保存，股东及出资需要后续补充")
                        .build());
        when(batchService.nextAutomationTarget("job-1")).thenReturn(Optional.of(
                CompanyBatchAutomationTarget.builder().companyId(13L).companyName("上海示例公司").build()));

        var result = service.capture(service.pairing().getPairingCode(), "job-1", 12L, request);

        assertThat(result.isCompleted()).isTrue();
        assertThat(result.getNextTarget().getCompanyId()).isEqualTo(13L);
    }
}
