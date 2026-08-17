package com.sentinel.ingestion.service;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * Maintains real-time sliding window metrics in Redis.
 * The detector reads these metrics to detect anomalies.
 *
 * Redis structures used:
 * - Sorted Set: metrics:{service}:{metric} → score=timestamp, member=value:uuid
 * - Hash: health:{service} → {error_rate, avg_latency, request_count, last_updated}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeMetricsService {

    private final StringRedisTemplate redis;

    private static final int WINDOW_MINUTES = 10;    // Keep 10 minutes of data
    private static final String METRICS_PREFIX = "metrics:";
    private static final String HEALTH_PREFIX = "health:";

    /**
     * Update all real-time metrics for a single log event (backward compatibility)
     */
    public void updateMetrics(LogEventDTO event) {
        updateMetricsBatch(java.util.Collections.singletonList(event));
    }

    /**
     * Update all real-time metrics for a batch of log events.
     * This dramatically improves performance by using Redis Pipelining and
     * only running expensive operations (updateHealthSummary, pruneOldData) ONCE per service.
     */
    public void updateMetricsBatch(java.util.List<LogEventDTO> events) {
        if (events == null || events.isEmpty()) return;

        long now = Instant.now().toEpochMilli();
        java.util.Set<String> affectedServices = new java.util.HashSet<>();
        for (LogEventDTO event : events) {
            affectedServices.add(event.getServiceName());
        }

        // Execute all counter increments and pruning in a single network trip via Pipelining
        redis.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
            @Override
            public Object execute(org.springframework.data.redis.core.RedisOperations operations) throws org.springframework.dao.DataAccessException {
                for (LogEventDTO event : events) {
                    String service = event.getServiceName();

                    // 1. Increment request count
                    pipelineIncrement(operations, service, "request_count", now);

                    // 2. Track error count if ERROR log
                    if (event.getLogLevel() == LogLevel.ERROR) {
                        pipelineIncrement(operations, service, "error_count", now);
                    }

                    // 3. Track latency if present
                    if (event.getLatencyMs() != null) {
                        pipelineTrackValue(operations, service, "latency_ms", event.getLatencyMs(), now);
                    }

                    // 4. Track 5xx status codes
                    if (event.getStatusCode() != null && event.getStatusCode() >= 500) {
                        pipelineIncrement(operations, service, "error_5xx_count", now);
                    }

                    // Track 429 Rate Limit status codes
                    if (event.getStatusCode() != null && event.getStatusCode() == 429) {
                        pipelineIncrement(operations, service, "error_429_count", now);
                    }
                }
                
                // Prune old data for affected services
                long cutoff = now - (WINDOW_MINUTES * 60 * 1000L);
                String[] metrics = {"request_count", "error_count", "latency_ms", "error_5xx_count", "error_429_count"};
                for (String service : affectedServices) {
                    for (String metric : metrics) {
                        String key = METRICS_PREFIX + service + ":" + metric;
                        operations.opsForZSet().removeRangeByScore(key, 0, cutoff);
                    }
                }
                return null;
            }
        });

        // Run the expensive summary operation ONCE per affected service (must be done after pipeline executes)
        for (String service : affectedServices) {
            updateHealthSummary(service);
        }
    }

    @SuppressWarnings("unchecked")
    private void pipelineIncrement(org.springframework.data.redis.core.RedisOperations operations, String service, String metric, long nowMs) {
        String key = METRICS_PREFIX + service + ":" + metric;
        String member = "1:" + nowMs + ":" + Math.random();
        operations.opsForZSet().add(key, member, nowMs);
        operations.expire(key, java.time.Duration.ofMinutes(WINDOW_MINUTES + 2));
    }

    @SuppressWarnings("unchecked")
    private void pipelineTrackValue(org.springframework.data.redis.core.RedisOperations operations, String service, String metric, double value, long nowMs) {
        String key = METRICS_PREFIX + service + ":" + metric;
        String member = value + ":" + nowMs + ":" + Math.random();
        operations.opsForZSet().add(key, member, nowMs);
        operations.expire(key, java.time.Duration.ofMinutes(WINDOW_MINUTES + 2));
    }

    private void updateHealthSummary(String service) {
        String key = HEALTH_PREFIX + service;
        long windowStart = Instant.now().minusSeconds(WINDOW_MINUTES * 60L).toEpochMilli();

        // Count requests and errors in window
        Long requestCount = redis.opsForZSet().count(
                METRICS_PREFIX + service + ":request_count", windowStart, Double.MAX_VALUE);
        Long errorCount = redis.opsForZSet().count(
                METRICS_PREFIX + service + ":error_count", windowStart, Double.MAX_VALUE);

        double errorRate = (requestCount != null && requestCount > 0 && errorCount != null)
                ? (double) errorCount / requestCount : 0.0;
                
        // Calculate p99 latency
        java.util.Set<String> latencies = redis.opsForZSet().rangeByScore(
                METRICS_PREFIX + service + ":latency_ms", windowStart, Double.MAX_VALUE);
        
        java.util.List<Double> latencyList = new java.util.ArrayList<>();
        if (latencies != null && !latencies.isEmpty()) {
            for (String member : latencies) {
                try {
                    latencyList.add(Double.parseDouble(member.split(":")[0]));
                } catch (Exception ignored) {}
            }
        }
        
        double p99Latency = 0.0;
        if (!latencyList.isEmpty()) {
            java.util.Collections.sort(latencyList);
            int index = (int) Math.ceil(99.0 / 100.0 * latencyList.size()) - 1;
            if (index < 0) index = 0;
            p99Latency = latencyList.get(index);
        }

        redis.opsForHash().put(key, "error_rate", String.format("%.4f", errorRate));
        redis.opsForHash().put(key, "p99_latency", String.format("%.2f", p99Latency));
        redis.opsForHash().put(key, "request_count", String.valueOf(requestCount != null ? requestCount : 0));
        redis.opsForHash().put(key, "error_count", String.valueOf(errorCount != null ? errorCount : 0));
        redis.opsForHash().put(key, "last_updated", String.valueOf(LocalDateTime.now()));
        redis.expire(key, WINDOW_MINUTES + 2, TimeUnit.MINUTES);
    }

    /**
     * Remove data older than the window to keep Redis lean
     */
    private void pruneOldData(String service, long nowMs) {
        long cutoff = nowMs - (WINDOW_MINUTES * 60 * 1000L);
        String[] metrics = {"request_count", "error_count", "latency_ms", "error_5xx_count", "error_429_count"};

        for (String metric : metrics) {
            String key = METRICS_PREFIX + service + ":" + metric;
            redis.opsForZSet().removeRangeByScore(key, 0, cutoff);
        }
    }

    /**
     * Get current error rate for a service (used by detector)
     */
    public double getErrorRate(String service) {
        String rate = (String) redis.opsForHash().get(HEALTH_PREFIX + service, "error_rate");
        return rate != null ? Double.parseDouble(rate) : 0.0;
    }

    /**
     * Get count of events in window (used by detector)
     */
    public long getCountInWindow(String service, String metric, int windowMinutes) {
        long windowStart = Instant.now().minusSeconds(windowMinutes * 60L).toEpochMilli();
        Long count = redis.opsForZSet().count(
                METRICS_PREFIX + service + ":" + metric, windowStart, Double.MAX_VALUE);
        return count != null ? count : 0L;
    }
}
