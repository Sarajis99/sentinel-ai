package com.sentinel.api.controller;

import com.sentinel.api.security.RateLimiterService;
import com.sentinel.api.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final RateLimiterService rateLimiterService;

    @PutMapping("/settings/api-key")
    public ResponseEntity<Map<String, String>> updateApiKey(@RequestBody ApiKeyRequest request) {
        settingsService.updateApiKey(request.getApiKey());
        String masked = settingsService.getMaskedApiKey();
        return ResponseEntity.ok(Map.of("maskedKey", masked != null ? masked : ""));
    }

    @GetMapping("/settings/api-key")
    public ResponseEntity<Map<String, String>> getApiKey() {
        String masked = settingsService.getMaskedApiKey();
        if (masked == null) {
            return ResponseEntity.ok(Map.of("maskedKey", ""));
        }
        return ResponseEntity.ok(Map.of("maskedKey", masked));
    }

    @GetMapping("/settings/test-llm")
    public ResponseEntity<Map<String, Object>> testLlmConnection(HttpServletRequest request) {
        String clientIp = rateLimiterService.getClientIp(request);
        if (!rateLimiterService.isAllowed("test-llm", clientIp, 5, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "success", false,
                    "message", "Rate limit exceeded. Please wait 1 minute before testing LLM connection again."
            ));
        }
        return ResponseEntity.ok(settingsService.testLLMConnection());
    }

    @PostMapping("/system/factory-reset")
    public ResponseEntity<Map<String, String>> factoryReset() {
        settingsService.factoryReset();
        return ResponseEntity.ok(Map.of("status", "success", "message", "System reset to factory defaults."));
    }

    @GetMapping("/settings/simulation")
    public ResponseEntity<Map<String, Object>> getSimulationConfig() {
        return ResponseEntity.ok(settingsService.getSimulationConfig());
    }

    @PutMapping("/settings/simulation")
    public ResponseEntity<Map<String, String>> updateSimulationConfig(@RequestBody SimulationConfigRequest request) {
        try {
            settingsService.updateSimulationConfig(request.getLogsPerSecond());
            return ResponseEntity.ok(Map.of("status", "success", "message", "Config updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/settings/debug-db")
    public ResponseEntity<Map<String, String>> debugDb() {
        try {
            settingsService.debugDbInsert();
            return ResponseEntity.ok(Map.of("status", "success", "message", "Insert worked"));
        } catch (Exception e) {
            String cause = e.getMessage();
            if (e.getCause() != null) cause += " | Cause: " + e.getCause().getMessage();
            if (e.getCause() != null && e.getCause().getCause() != null) cause += " | Root: " + e.getCause().getCause().getMessage();
            return ResponseEntity.ok(Map.of("status", "error", "message", cause));
        }
    }

    @Data
    public static class ApiKeyRequest {
        private String apiKey;
    }

    @Data
    public static class SimulationConfigRequest {
        private int logsPerSecond;
    }
}
