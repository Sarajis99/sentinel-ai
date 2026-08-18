package com.sentinel.detector.engine;

import com.sentinel.common.enums.Severity;
import com.sentinel.detector.config.DetectionConfig;
import com.sentinel.detector.model.AnomalySignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Z-Score Analyser — detects statistical outliers.
 *
 * For each metric window it:
 *  1. Reads all values from the Redis sorted set (metrics:{service}:{metric})
 *  2. Calculates mean and standard deviation
 *  3. Reads the *latest* value
 *  4. If |latest - mean| / std-dev exceeds threshold → fires anomaly
 *
 * This catches spikes in latency and 5xx counts that deviate from normal behaviour.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZScoreAnalyser {

    private final StringRedisTemplate redis;
    private final DetectionConfig config;

    private static final String METRICS_PREFIX = "metrics:";

    /**
     * Analyse a single (service, metric) pair.
     * Returns an AnomalySignal if a statistical outlier is detected.
     */
    public Optional<AnomalySignal> analyse(String service, String metric) {
        String key = METRICS_PREFIX + service + ":" + metric;
        long windowStart = Instant.now().minusSeconds(config.getWindowMinutes() * 60L).toEpochMilli();

        // Fetch all members in the time window
        Set<String> members = redis.opsForZSet().rangeByScore(key, windowStart, Double.MAX_VALUE);
        if (members == null || members.size() < config.getMinRequestsForDetection()) {
            return Optional.empty();
        }

        // Parse values (member format: "value:timestamp:random")
        double[] values = members.stream()
                .mapToDouble(this::parseValue)
                .filter(v -> !Double.isNaN(v))
                .toArray();

        if (values.length < 2) {
            return Optional.empty();
        }

        double mean = calculateMean(values);
        double stdDev = calculateStdDev(values, mean);

        if (stdDev == 0.0) {
            return Optional.empty();  // No variation, nothing to detect
        }

        // The latest value is the one with the highest score (timestamp)
        String latestMember = redis.opsForZSet().reverseRangeByScore(key, windowStart, Double.MAX_VALUE, 0, 1)
                .stream().findFirst().orElse(null);
        if (latestMember == null) {
            return Optional.empty();
        }

        long latestTimestamp = parseTimestamp(latestMember);
        // Prevent "ghost" anomalies: if the data hasn't been updated in 60s, it's a stale spike
        if (latestTimestamp > 0 && (System.currentTimeMillis() - latestTimestamp > 60_000)) {
            return Optional.empty();
        }

        double latestValue = parseValue(latestMember);
        double zScore = Math.abs(latestValue - mean) / stdDev;

        Severity severity = classifySeverity(zScore);
        if (severity == null) {
            return Optional.empty();
        }

        log.warn("🔍 Z-Score anomaly detected: service={} metric={} zScore={:.2f} latest={} mean={:.2f}",
                service, metric, zScore, latestValue, mean);

        return Optional.of(AnomalySignal.builder()
                .serviceName(service)
                .metricName(metric)
                .expectedValue(mean)
                .actualValue(latestValue)
                .zScore(zScore)
                .severity(severity)
                .description(String.format(
                        "Z-Score anomaly on %s for %s: z=%.2f (current=%.0f, mean=%.2f, stdDev=%.2f)",
                        metric, service, zScore, latestValue, mean, stdDev))
                .windowMinutes(config.getWindowMinutes())
                .build());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double parseValue(String member) {
        try {
            // Format: "value:timestamp:random" or "1:timestamp:random" (counter)
            String[] parts = member.split(":");
            return Double.parseDouble(parts[0]);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private long parseTimestamp(String member) {
        try {
            String[] parts = member.split(":");
            if (parts.length > 1) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception e) {}
        return 0L;
    }

    private double calculateMean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private double calculateStdDev(double[] values, double mean) {
        double variance = 0;
        for (double v : values) variance += Math.pow(v - mean, 2);
        return Math.sqrt(variance / values.length);
    }

    private Severity classifySeverity(double zScore) {
        if (zScore >= config.getZScoreP0Threshold()) return Severity.P0;
        if (zScore >= config.getZScoreP1Threshold()) return Severity.P1;
        if (zScore >= config.getZScoreP2Threshold()) return Severity.P2;
        return null;
    }
}
