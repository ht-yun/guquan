package com.guquan.equity.service;

import com.guquan.equity.model.CompanyBatchAutomationTarget;
import com.guquan.equity.model.CompanyBatchCompanyView;
import com.guquan.equity.model.CompanyBatchStatus;
import com.guquan.equity.model.CompanyAllSectionsView;
import com.guquan.equity.model.EdgeExtensionCaptureRequest;
import com.guquan.equity.model.EdgeExtensionCaptureResult;
import com.guquan.equity.model.EdgeExtensionPairingView;
import com.guquan.equity.model.EdgeExtensionTargetView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EdgeExtensionBridgeService {

    private final CompanyBatchService batchService;
    private final String pairingCode = UUID.randomUUID().toString().replace("-", "");

    public EdgeExtensionBridgeService(CompanyBatchService batchService) {
        this.batchService = batchService;
    }

    public EdgeExtensionPairingView pairing() {
        return EdgeExtensionPairingView.builder()
                .pairingCode(pairingCode)
                .apiBasePath("/api/edge-extension")
                .build();
    }

    public EdgeExtensionTargetView nextTarget(String pairingCode, String jobId) {
        authorize(pairingCode);
        return batchService.nextAutomationTarget(jobId)
                .map(target -> targetView(jobId, target))
                .orElse(null);
    }

    public EdgeExtensionCaptureResult capture(String pairingCode, String jobId, Long companyId,
            EdgeExtensionCaptureRequest request) {
        authorize(pairingCode);
        if (request == null || !StringUtils.hasText(request.getPageText())) {
            throw new IllegalArgumentException("没有读取到当前企业页面文字");
        }
        if (request.getPageText().length() < 80) {
            throw new IllegalArgumentException("当前页面文字过少，请确认已经打开企业详情页");
        }
        if (!isGsxtUrl(request.getCurrentUrl())) {
            throw new IllegalArgumentException("当前页面不是国家企业信用信息公示系统企业详情页");
        }

        CompanyAllSectionsView parsed = batchService.confirmAllSections(
                jobId, companyId, request.getPageText(), request.getCurrentUrl());
        CompanyBatchCompanyView current = batchService.company(jobId, companyId);
        boolean profileConfirmed = current.getProfile() != null
                && StringUtils.hasText(current.getProfile().getCompanyName())
                && StringUtils.hasText(current.getProfile().getCreditCode());
        if (current.getStatus() == CompanyBatchStatus.CONFLICT || !profileConfirmed) {
            return EdgeExtensionCaptureResult.builder()
                    .completed(false)
                    .message(StringUtils.hasText(current.getCollectionMessage())
                            ? current.getCollectionMessage()
                            : "页面已经解析，但未能确认企业名称和统一社会信用代码，请停留当前企业人工复核")
                    .parsed(parsed)
                    .build();
        }

        EdgeExtensionTargetView next = batchService.nextAutomationTarget(jobId)
                .map(target -> targetView(jobId, target))
                .orElse(null);
        return EdgeExtensionCaptureResult.builder()
                .completed(true)
                .message(next == null ? "当前任务中的企业已经全部采集完成" : "当前企业已保存，准备采集下一家")
                .parsed(parsed)
                .nextTarget(next)
                .build();
    }

    private boolean isGsxtUrl(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && ("gsxt.gov.cn".equalsIgnoreCase(host)
                            || host.toLowerCase().endsWith(".gsxt.gov.cn"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private EdgeExtensionTargetView targetView(String jobId, CompanyBatchAutomationTarget target) {
        return EdgeExtensionTargetView.builder()
                .jobId(jobId)
                .companyId(target.getCompanyId())
                .companyName(target.getCompanyName())
                .creditCode(target.getCreditCode())
                .build();
    }

    private void authorize(String suppliedCode) {
        byte[] expected = pairingCode.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = (suppliedCode == null ? "" : suppliedCode).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new IllegalArgumentException("Edge 扩展配对码无效，请从项目页面重新复制配对信息");
        }
    }
}
