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
 * Moving Average Latency Analyser.
 *
 * Computes:
 *  - Short window mean (config.windowMinutes, e.g. 5 min) = "current" latency
 *  - Long window mean  (config.movingAverageLookbackMinutes, e.g. 15 min) = "baseline"
 *
 * If current latency deviates more than `latencyDeviationThreshold` (default 50%)
 * from the baseline, an anomaly is raised.
 *
 * Catches gradual latency degradation that Z-Score misses because the std-dev
 * widens with the degradation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MovingAverageAnalyser {

    private final StringRedisTemplate redis;
    private final DetectionConfig config;

    private static final String METRICS_PREFIX = "metrics:";

    /**
     * Analyses latency for a service using short-vs-long moving average comparison.
     */
    public Optional<AnomalySignal> analyse(String service) {
        String key = METRICS_PREFIX + service + ":latency_ms";

        long now = Instant.now().toEpochMilli();
        long shortWindowStart = now - (config.getWindowMinutes() * 60_000L);
        long longWindowStart  = now - (config.getMovingAverageLookbackMinutes() * 60_000L);

        double shortMean = computeMean(key, shortWindowStart, now);
        double longMean  = computeMean(key, longWindowStart, now);

        if (longMean == 0.0 || shortMean == 0.0) {
            return Optional.empty();  // Not enough data
        }

        double deviation = (shortMean - longMean) / longMean;

        if (deviation < config.getLatencyDeviationThreshold()) {
            return Optional.empty();
        }

        // Severity: >100% deviation = P1, >50% = P2
        Severity severity = (deviation >= 1.0) ? Severity.P1 : Severity.P2;

        log.warn("⏱️ Latency degradation detected: service={} shortMean={:.0f}ms longMean={:.0f}ms deviation={:.1%}",
                service, shortMean, longMean, deviation);

        return Optional.of(AnomalySignal.builder()
                .serviceName(service)
                .metricName("latency_ms")
                .expectedValue(longMean)
                .actualValue(shortMean)
                .zScore(0.0)
                .severity(severity)
                .description(String.format(
                        "Latency degradation on %s: current=%.0fms baseline=%.0fms (+%.0f%%)",
                        service, shortMean, longMean, deviation * 100))
                .windowMinutes(config.getWindowMinutes())
                .build());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double computeMean(String key, long from, long to) {
        Set<String> members = redis.opsForZSet().rangeByScore(key, from, to);
        if (members == null || members.isEmpty()) {
            return 0.0;
        }

        double sum = 0;
        int count = 0;
        for (String member : members) {
            try {
                double val = Double.parseDouble(member.split(":")[0]);
                sum += val;
                count++;
            } catch (NumberFormatException ignored) {
                // skip malformed members
            }
        }
        return count > 0 ? sum / count : 0.0;
    }
}
