package com.sentinel.detector.service;

import com.sentinel.detector.config.DetectionConfig;
import com.sentinel.detector.engine.ErrorRateAnalyser;
import com.sentinel.detector.engine.MovingAverageAnalyser;
import com.sentinel.detector.engine.ZScoreAnalyser;
import com.sentinel.detector.model.AnomalySignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AnomalyDetector — the central orchestrator for Phase 2.
 *
 * Runs on a fixed schedule (every 30s by default).
 * For each service in Redis it runs all three analysers in sequence
 * and publishes any detected anomalies to the anomaly-events Kafka topic.
 *
 * Deduplication: A simple Redis key prevents the same anomaly type from
 * being re-published within a 5-minute window (avoids alert storms).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetector {

    private final ZScoreAnalyser      zScoreAnalyser;
    private final ErrorRateAnalyser   errorRateAnalyser;
    private final MovingAverageAnalyser movingAverageAnalyser;
    private final AnomalyPublisher    publisher;
    private final StringRedisTemplate redis;
    private final DetectionConfig     config;

    // Known services — in production this could be dynamically discovered from Redis
    private static final List<String> MONITORED_SERVICES = List.of(
            "payment-service",
            "order-service",
            "inventory-service",
            "notification-service",
            "user-service"
    );

    // Metrics to run Z-Score analysis on
    private static final List<String> Z_SCORE_METRICS = List.of(
            "latency_ms",
            "error_5xx_count",
            "error_count",
            "error_429_count"
    );

    /**
     * Main detection loop — runs every 30 seconds.
     * Uses fixedDelayString so it reads from config (ms).
     */
    @Scheduled(fixedDelayString = "${detection.detector-interval-ms:30000}",
               initialDelay = 15_000)  // Wait 15s on startup for data to accumulate
    public void runDetection() {
        log.debug("🔍 Running anomaly detection cycle...");
        int anomaliesFound = 0;

        for (String service : MONITORED_SERVICES) {
            anomaliesFound += runDetectionForService(service);
        }

        if (anomaliesFound > 0) {
            log.warn("🚨 Detection cycle complete — {} anomalies detected and published", anomaliesFound);
        } else {
            log.debug("✅ Detection cycle complete — all services healthy");
        }
    }

    // ─── Per-service detection ────────────────────────────────────────────────

    int runDetectionForService(String service) {
        int count = 0;

        // 1. Error rate threshold check (fast path, uses pre-computed Redis hash)
        Optional<AnomalySignal> errorRateSignal = errorRateAnalyser.analyse(service);
        if (errorRateSignal.isPresent() && shouldPublish(service, "error_rate")) {
            publisher.publish(errorRateSignal.get());
            markPublished(service, "error_rate");
            count++;
        }

        // 2. Moving average latency deviation
        Optional<AnomalySignal> latencySignal = movingAverageAnalyser.analyse(service);
        if (latencySignal.isPresent() && shouldPublish(service, "latency_moving_avg")) {
            publisher.publish(latencySignal.get());
            markPublished(service, "latency_moving_avg");
            count++;
        }

        // 3. Z-Score per metric
        for (String metric : Z_SCORE_METRICS) {
            Optional<AnomalySignal> zSignal = zScoreAnalyser.analyse(service, metric);
            if (zSignal.isPresent() && shouldPublish(service, "zscore_" + metric)) {
                publisher.publish(zSignal.get());
                markPublished(service, "zscore_" + metric);
                count++;
            }
        }

        return count;
    }

    // ─── Deduplication ────────────────────────────────────────────────────────

    /**
     * Returns true if we have NOT already published this anomaly type
     * for this service within the deduplication window.
     */
    private boolean shouldPublish(String service, String anomalyKey) {
        String dedupKey = "dedup:anomaly:" + service + ":" + anomalyKey;
        return !Boolean.TRUE.equals(redis.hasKey(dedupKey));
    }

    /**
     * Sets a Redis key to prevent re-publishing the same anomaly for 5 minutes.
     */
    private void markPublished(String service, String anomalyKey) {
        String dedupKey = "dedup:anomaly:" + service + ":" + anomalyKey;
        redis.opsForValue().set(dedupKey, "1");
        redis.expire(dedupKey, java.time.Duration.ofMinutes(5));
    }
}
