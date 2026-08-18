package com.sentinel.detector.engine;

import com.sentinel.common.enums.AnomalyType;
import com.sentinel.common.enums.Severity;
import com.sentinel.detector.config.DetectionConfig;
import com.sentinel.detector.model.AnomalySignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Error Rate Spike Analyser.
 *
 * Reads the pre-computed error_rate from the Redis health hash
 * (populated by sentinel-ingestion's RealTimeMetricsService) and
 * checks it against configured thresholds.
 *
 * This is separate from Z-Score because:
 * - Error rate is already normalised (0.0 – 1.0)
 * - Business thresholds (e.g., >10% = P1) are clearer than z-score for ops teams
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorRateAnalyser {

    private final StringRedisTemplate redis;
    private final DetectionConfig config;

    private static final String HEALTH_PREFIX = "health:";

    /**
     * Analyses the current error rate for a service.
     * Returns AnomalySignal if the rate exceeds configured thresholds.
     */
    public Optional<AnomalySignal> analyse(String service) {
        String key = HEALTH_PREFIX + service;

        String rateStr = (String) redis.opsForHash().get(key, "error_rate");
        String reqStr  = (String) redis.opsForHash().get(key, "request_count");

        if (rateStr == null || reqStr == null) {
            return Optional.empty();
        }

        String lastUpdatedStr = (String) redis.opsForHash().get(key, "last_updated");
        long lastUpdated = 0;
        if (lastUpdatedStr != null) {
            try {
                lastUpdated = java.time.LocalDateTime.parse(lastUpdatedStr)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
            } catch (Exception e) {
                // fallback
            }
        }
        
        // Prevent "ghost" anomalies: if the data hasn't been updated in 60s, it's a stale spike
        if (System.currentTimeMillis() - lastUpdated > 60_000) {
            return Optional.empty();
        }

        double errorRate;
        long requestCount;
        try {
            errorRate    = Double.parseDouble(rateStr);
            requestCount = Long.parseLong(reqStr);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        // Need a minimum number of requests to avoid noise
        if (requestCount < config.getMinRequestsForDetection()) {
            return Optional.empty();
        }

        Severity severity = classifySeverity(errorRate);
        if (severity == null) {
            return Optional.empty();
        }

        log.warn("🚨 Error rate spike detected: service={} errorRate={:.2%} severity={}",
                service, errorRate, severity);

        return Optional.of(AnomalySignal.builder()
                .serviceName(service)
                .metricName("error_rate")
                .expectedValue(config.getErrorRateP2Threshold())
                .actualValue(errorRate)
                .zScore(0.0)   // Not applicable for threshold-based detection
                .severity(severity)
                .description(String.format(
                        "Error rate spike on %s: %.1f%% (threshold P%s=%.0f%%)",
                        service,
                        errorRate * 100,
                        severity.name().replace("P", ""),
                        getThreshold(severity) * 100))
                .windowMinutes(config.getWindowMinutes())
                .build());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Severity classifySeverity(double errorRate) {
        if (errorRate >= config.getErrorRateP0Threshold()) return Severity.P0;
        if (errorRate >= config.getErrorRateP1Threshold()) return Severity.P1;
        if (errorRate >= config.getErrorRateP2Threshold()) return Severity.P2;
        return null;
    }

    private double getThreshold(Severity severity) {
        return switch (severity) {
            case P0 -> config.getErrorRateP0Threshold();
            case P1 -> config.getErrorRateP1Threshold();
            default -> config.getErrorRateP2Threshold();
        };
    }
}
