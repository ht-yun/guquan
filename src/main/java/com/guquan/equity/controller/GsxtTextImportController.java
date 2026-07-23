package com.guquan.equity.controller;

import com.guquan.equity.api.CompanyProfileCacheService;
import com.guquan.equity.model.CompanyBrowserTaskRequest;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.GsxtTextImportRequest;
import com.guquan.equity.service.GsxtPageParser;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/company/import")
public class GsxtTextImportController {

    private static final int MAX_TEXT_LENGTH = 2_000_000;

    private final GsxtPageParser parser;
    private final CompanyProfileCacheService cacheService;

    public GsxtTextImportController(GsxtPageParser parser, CompanyProfileCacheService cacheService) {
        this.parser = parser;
        this.cacheService = cacheService;
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody GsxtTextImportRequest request) {
        List<CompanyProfile> candidates = parse(request);
        return Map.of(
                "candidateCount", candidates.size(),
                "candidates", candidates,
                "message", candidates.isEmpty()
                        ? "未识别到有效的统一社会信用代码，请确认复制了企业结果页或详情页内容"
                        : "解析完成，请核对后再保存");
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody GsxtTextImportRequest request) {
        List<CompanyProfile> candidates = request != null && request.getProfiles() != null
                && !request.getProfiles().isEmpty()
                ? request.getProfiles() : parse(request);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("没有可保存的企业信息");
        }
        List<CompanyProfile> saved = cacheService.saveAll(candidates);
        return Map.of(
                "savedCount", saved.size(),
                "profiles", saved,
                "message", "官网页面内容已解析并保存到本地档案");
    }

    private List<CompanyProfile> parse(GsxtTextImportRequest request) {
        if (request == null || !StringUtils.hasText(request.getPageText())) {
            throw new IllegalArgumentException("请粘贴国家企业信用信息公示系统页面内容");
        }
        if (request.getPageText().length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("粘贴内容过大，请仅复制企业结果或详情区域");
        }
        CompanyBrowserTaskRequest parserRequest = new CompanyBrowserTaskRequest();
        parserRequest.setCompanyName(request.getCompanyName());
        return parser.parse(request.getPageText(), parserRequest, "GSXT_MANUAL_IMPORT");
    }
}
