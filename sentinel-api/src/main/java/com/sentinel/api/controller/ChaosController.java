package com.sentinel.api.controller;

import com.sentinel.api.service.ChaosService;
import com.sentinel.common.dto.LogEventDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/chaos")
@RequiredArgsConstructor
public class ChaosController {

    private final ChaosService chaosService;

    @PostMapping("/inject")
    public ResponseEntity<?> injectChaos(@RequestBody ChaosInjectionRequest request) {
        log.info("🧪 POST /chaos/inject — Injecting {} on {}", request.getScenario(), request.getTargetService());

        try {
            List<LogEventDTO> events = chaosService.injectAnomaly(request.getScenario(), request.getTargetService());
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Injected " + events.size() + " anomaly events.",
                    "events", events
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "error",
                    "message", "Invalid scenario or service."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<Map<String, String>>> getScenarios() {
        return ResponseEntity.ok(chaosService.getAvailableScenarios());
    }

    @GetMapping("/services")
    public ResponseEntity<List<String>> getServices() {
        return ResponseEntity.ok(chaosService.getAvailableServices());
    }

    @Data
    public static class ChaosInjectionRequest {
        private String scenario;
        private String targetService;
    }
}
