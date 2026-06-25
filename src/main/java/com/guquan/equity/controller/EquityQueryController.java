package com.guquan.equity.controller;

import com.guquan.equity.api.EquityDataProvider;
import com.guquan.equity.api.EquityQueryService;
import com.guquan.equity.model.EquityQueryRequest;
import com.guquan.equity.model.EquityQueryRecordResponse;
import com.guquan.equity.model.EquityQueryResult;
import com.guquan.equity.model.PersonSearchRequest;
import com.guquan.equity.model.PersonSearchResult;
import com.guquan.equity.repository.EquityQueryRecordRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equity")
public class EquityQueryController {

    private final EquityQueryService equityQueryService;
    private final EquityDataProvider equityDataProvider;
    private final EquityQueryRecordRepository recordRepository;

    public EquityQueryController(
            EquityQueryService equityQueryService,
            EquityDataProvider equityDataProvider,
            EquityQueryRecordRepository recordRepository) {
        this.equityQueryService = equityQueryService;
        this.equityDataProvider = equityDataProvider;
        this.recordRepository = recordRepository;
    }

    @PostMapping("/query")
    public EquityQueryResult query(@RequestBody EquityQueryRequest request) {
        return equityQueryService.query(request);
    }

    @GetMapping("/provider")
    public Map<String, Object> provider() {
        boolean mock = "MOCK".equalsIgnoreCase(equityDataProvider.providerName());
        return Map.of(
                "providerName", equityDataProvider.providerName(),
                "realData", !mock,
                "message", mock
                        ? "当前使用 Mock 演示数据源，只能验证流程，不能代表真实工商或上市公司持股结果。"
                        : "当前使用真实数据源，请结合 source 和 sourceUpdatedAt 判断数据时效。"
        );
    }

    @GetMapping("/records/{queryId}")
    public ResponseEntity<EquityQueryRecordResponse> getRecord(@PathVariable String queryId) {
        return recordRepository.findByQueryId(queryId)
                .map(EquityQueryRecordResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/records")
    public List<EquityQueryRecordResponse> listRecords(
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return recordRepository.findAll(
                        PageRequest.of(0, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(EquityQueryRecordResponse::from)
                .toList();
    }

    @PostMapping("/person-search")
    public PersonSearchResult searchPerson(@RequestBody PersonSearchRequest request) {
        return equityQueryService.searchPerson(request);
    }
}
