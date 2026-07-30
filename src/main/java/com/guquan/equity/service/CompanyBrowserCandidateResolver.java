package com.guquan.equity.service;

import com.guquan.equity.model.CompanyProfile;
import com.guquan.equity.util.CompanyNameNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Decides whether a GSXT result can be opened without human confirmation.
 * A name match must be exact after the project's conservative normalization.
 */
@Component
public class CompanyBrowserCandidateResolver {

    public Resolution resolve(String requestedName, String requestedCreditCode, List<CompanyProfile> candidates) {
        List<CompanyProfile> unique = unique(candidates);
        if (unique.isEmpty()) {
            return new Resolution(List.of(), null, false);
        }
        if (StringUtils.hasText(requestedCreditCode)) {
            String expected = requestedCreditCode.trim().toUpperCase(Locale.ROOT);
            List<CompanyProfile> codes = unique.stream()
                    .filter(candidate -> expected.equals(normalizeCode(candidate.getCreditCode())))
                    .toList();
            if (codes.size() == 1) {
                return new Resolution(unique, codes.get(0), false);
            }
        }
        if (StringUtils.hasText(requestedName)) {
            String expected = CompanyNameNormalizer.normalize(requestedName);
            List<CompanyProfile> names = unique.stream()
                    .filter(candidate -> expected.equals(CompanyNameNormalizer.normalize(candidate.getCompanyName())))
                    .toList();
            if (names.size() == 1) {
                return new Resolution(unique, names.get(0), false);
            }
            if (names.size() > 1) {
                return new Resolution(names, null, true);
            }
            // A fuzzy GSXT search can return one similarly named company. Do not
            // treat that as a verified match merely because it is the only row.
            return new Resolution(unique, null, true);
        }
        return unique.size() == 1
                ? new Resolution(unique, unique.get(0), false)
                : new Resolution(unique, null, true);
    }

    private List<CompanyProfile> unique(List<CompanyProfile> candidates) {
        if (candidates == null) {
            return List.of();
        }
        Map<String, CompanyProfile> result = new LinkedHashMap<>();
        for (CompanyProfile candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getCompanyName())) {
                continue;
            }
            String key = StringUtils.hasText(candidate.getCreditCode())
                    ? normalizeCode(candidate.getCreditCode())
                    : CompanyNameNormalizer.normalize(candidate.getCompanyName());
            if (StringUtils.hasText(key)) {
                result.putIfAbsent(key, candidate);
            }
        }
        return new ArrayList<>(result.values());
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    public record Resolution(List<CompanyProfile> candidates, CompanyProfile automaticCandidate,
            boolean requiresSelection) {
    }
}
