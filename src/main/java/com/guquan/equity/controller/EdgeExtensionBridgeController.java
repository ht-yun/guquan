package com.guquan.equity.controller;

import com.guquan.equity.model.EdgeExtensionCaptureRequest;
import com.guquan.equity.model.EdgeExtensionCaptureResult;
import com.guquan.equity.model.EdgeExtensionPairingView;
import com.guquan.equity.model.EdgeExtensionTargetView;
import com.guquan.equity.service.EdgeExtensionBridgeService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/edge-extension")
@CrossOrigin(originPatterns = {"chrome-extension://*", "edge-extension://*"},
        allowedHeaders = {"Content-Type", "X-Extension-Token"},
        methods = {})
public class EdgeExtensionBridgeController {

    private final EdgeExtensionBridgeService service;

    public EdgeExtensionBridgeController(EdgeExtensionBridgeService service) {
        this.service = service;
    }

    @GetMapping("/pairing")
    public EdgeExtensionPairingView pairing() {
        return service.pairing();
    }

    @GetMapping("/jobs/{jobId}/next")
    public EdgeExtensionTargetView next(@RequestHeader("X-Extension-Token") String pairingCode,
            @PathVariable String jobId) {
        return service.nextTarget(pairingCode, jobId);
    }

    @PostMapping("/jobs/{jobId}/companies/{companyId}/capture")
    public EdgeExtensionCaptureResult capture(@RequestHeader("X-Extension-Token") String pairingCode,
            @PathVariable String jobId, @PathVariable Long companyId,
            @RequestBody EdgeExtensionCaptureRequest request) {
        return service.capture(pairingCode, jobId, companyId, request);
    }
}
