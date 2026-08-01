package com.sentinel.api.controller;

import com.sentinel.api.service.DataRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * DataRetentionController — Manual trigger for data retention cleanup.
 *
 * While the DataRetentionService runs automatically via @Scheduled cron,
 * this controller allows manual triggering for testing/debugging.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class DataRetentionController {

    private final DataRetentionService dataRetentionService;

    /**
     * POST /api/v1/system/retention-cleanup
     * Manually triggers the data retention cleanup job.
     */
    @PostMapping("/retention-cleanup")
    public ResponseEntity<Map<String, String>> triggerCleanup() {
        log.info("🧹 POST /system/retention-cleanup — Manual trigger");
        dataRetentionService.triggerManualCleanup();
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", "Data retention cleanup completed successfully."
        ));
    }
}
