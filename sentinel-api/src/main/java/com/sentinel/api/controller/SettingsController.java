package com.sentinel.api.controller;

import com.sentinel.api.service.SettingsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @PutMapping("/settings/api-key")
    public ResponseEntity<Map<String, String>> updateApiKey(@RequestBody ApiKeyRequest request) {
        settingsService.updateApiKey(request.getApiKey());
        return ResponseEntity.ok(Map.of("maskedKey", settingsService.getMaskedApiKey()));
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
    public ResponseEntity<Map<String, Object>> testLlmConnection() {
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

    @Data
    public static class ApiKeyRequest {
        private String apiKey;
    }

    @Data
    public static class SimulationConfigRequest {
        private int logsPerSecond;
    }
}
