package com.guquan.equity.controller;

import com.guquan.equity.api.CompanyInfoProvider;
import com.guquan.equity.api.CompanyLookupService;
import com.guquan.equity.api.CompanyProfileCacheService;
import com.guquan.equity.model.CompanyLookupRequest;
import com.guquan.equity.model.CompanyLookupResult;
import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.model.CreditCodeParseResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
public class CompanyLookupController {

    private final CompanyLookupService companyLookupService;
    private final CompanyProfileCacheService companyProfileCacheService;
    private final List<CompanyInfoProvider> providers;

    public CompanyLookupController(
            CompanyLookupService companyLookupService,
            CompanyProfileCacheService companyProfileCacheService,
            List<CompanyInfoProvider> providers) {
        this.companyLookupService = companyLookupService;
        this.companyProfileCacheService = companyProfileCacheService;
        this.providers = providers;
    }

    @PostMapping("/lookup")
    public CompanyLookupResult lookup(@RequestBody CompanyLookupRequest request) {
        return companyLookupService.lookup(request);
    }

    @PostMapping("/profiles")
    public CompanyProfile saveProfile(@RequestBody CompanyProfile profile) {
        return companyProfileCacheService.save(profile);
    }

    @PostMapping("/profiles/batch")
    public Map<String, Object> saveProfilesBatch(@RequestBody List<CompanyProfile> profiles) {
        List<CompanyProfile> saved = companyProfileCacheService.saveAll(profiles);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("savedCount", saved.size());
        result.put("profiles", saved);
        return result;
    }

    @PostMapping("/lookup/batch")
    public List<CompanyLookupResult> lookupBatch(@RequestBody List<CompanyLookupRequest> requests) {
        return requests.stream().map(companyLookupService::lookup).toList();
    }

    @GetMapping("/credit-code/parse")
    public CreditCodeParseResult parseCreditCode(@RequestParam String creditCode) {
        return companyProfileCacheService.parseCreditCode(creditCode);
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return Map.of(
                "providers", providers.stream().map(CompanyInfoProvider::providerName).toList(),
                "message", "当前仅启用本地企业档案缓存。");
    }
}
