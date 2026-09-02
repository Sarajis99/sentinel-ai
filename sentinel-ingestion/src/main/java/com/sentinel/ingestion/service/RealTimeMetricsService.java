package com.sentinel.ingestion.service;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.dao.DataAccessException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeMetricsService {

    private final StringRedisTemplate redis;

    private static final int WINDOW_MINUTES = 10;
    private static final String METRICS_PREFIX = "metrics:";
    private static final String HEALTH_PREFIX = "health:";
    
    // Track services that had activity recently so we know which to summarize
    private final Set<String> activeServices = ConcurrentHashMap.newKeySet();
    
    // Add import for ConcurrentHashMap
    private static final java.util.Map<String, Boolean> knownServices = new java.util.concurrent.ConcurrentHashMap<>();

    public void updateMetrics(LogEventDTO event) {
        updateMetricsBatch(Collections.singletonList(event));
    }

    public void updateMetricsBatch(List<LogEventDTO> events) {
        if (events == null || events.isEmpty()) return;

        long now = Instant.now().toEpochMilli();
        Set<String> affectedServices = new HashSet<>();
        
        for (LogEventDTO event : events) {
            String svc = event.getServiceName();
            if (svc != null) {
                affectedServices.add(svc);
                knownServices.put(svc, true);
            }
        }

        redis.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (LogEventDTO event : events) {
                    String service = event.getServiceName();
                    if (service == null) continue;

                    pipelineIncrement(operations, service, "request_count", now);

                    if (event.getLogLevel() == LogLevel.ERROR) {
                        pipelineIncrement(operations, service, "error_count", now);
                    }

                    if (event.getLatencyMs() != null) {
                        pipelineTrackValue(operations, service, "latency_ms", event.getLatencyMs(), now);
                    }

                    if (event.getStatusCode() != null && event.getStatusCode() >= 500) {
                        pipelineIncrement(operations, service, "error_5xx_count", now);
                    }

                    if (event.getStatusCode() != null && event.getStatusCode() == 429) {
                        pipelineIncrement(operations, service, "error_429_count", now);
                    }
                }
                return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void pipelineIncrement(RedisOperations operations, String service, String metric, long nowMs) {
        String key = METRICS_PREFIX + service + ":" + metric;
        String member = "1:" + nowMs + ":" + Math.random();
        operations.opsForZSet().add(key, member, nowMs);
        // Removed aggressive expiration per-item. Handled by Scheduled task.
    }

    @SuppressWarnings("unchecked")
    private void pipelineTrackValue(RedisOperations operations, String service, String metric, double value, long nowMs) {
        String key = METRICS_PREFIX + service + ":" + metric;
        String member = value + ":" + nowMs + ":" + Math.random();
        operations.opsForZSet().add(key, member, nowMs);
        // Removed aggressive expiration per-item. Handled by Scheduled task.
    }

    /**
     * Run health summary and pruning every 5 seconds for all known services.
     * This decouples heavy read/writes from the log stream, saving massive amounts of Redis commands.
     */
    @Scheduled(fixedRate = 5000)
    public void scheduledHealthSummary() {
        if (knownServices.isEmpty()) return;
        
        long nowMs = Instant.now().toEpochMilli();
        for (String service : knownServices.keySet()) {
            try {
                updateHealthSummary(service, nowMs);
                pruneOldDataAndExpire(service, nowMs);
            } catch (Exception e) {
                log.error("Failed to update health summary for service {}", service, e);
            }
        }
    }

    private void updateHealthSummary(String service, long nowMs) {
        String key = HEALTH_PREFIX + service;
        long windowStart = nowMs - (WINDOW_MINUTES * 60 * 1000L);

        Long requestCount = redis.opsForZSet().count(
                METRICS_PREFIX + service + ":request_count", windowStart, Double.MAX_VALUE);
        Long errorCount = redis.opsForZSet().count(
                METRICS_PREFIX + service + ":error_count", windowStart, Double.MAX_VALUE);

        double errorRate = (requestCount != null && requestCount > 0 && errorCount != null)
                ? (double) errorCount / requestCount : 0.0;
                
        Set<String> latencies = redis.opsForZSet().rangeByScore(
                METRICS_PREFIX + service + ":latency_ms", windowStart, Double.MAX_VALUE);
        
        List<Double> latencyList = new ArrayList<>();
        if (latencies != null && !latencies.isEmpty()) {
            for (String member : latencies) {
                try {
                    latencyList.add(Double.parseDouble(member.split(":")[0]));
                } catch (Exception ignored) {}
            }
        }
        
        double p99Latency = 0.0;
        if (!latencyList.isEmpty()) {
            Collections.sort(latencyList);
            int index = (int) Math.ceil(99.0 / 100.0 * latencyList.size()) - 1;
            if (index < 0) index = 0;
            p99Latency = latencyList.get(index);
        }

        final double finalP99Latency = p99Latency;
        final double finalErrorRate = errorRate;

        // Pipeline the hash puts to save commands
        redis.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.opsForHash().put(key, "error_rate", String.format("%.4f", finalErrorRate));
                operations.opsForHash().put(key, "p99_latency", String.format("%.2f", finalP99Latency));
                operations.opsForHash().put(key, "request_count", String.valueOf(requestCount != null ? requestCount : 0));
                operations.opsForHash().put(key, "error_count", String.valueOf(errorCount != null ? errorCount : 0));
                operations.opsForHash().put(key, "last_updated", String.valueOf(LocalDateTime.now()));
                operations.expire(key, Duration.ofMinutes(WINDOW_MINUTES + 2));
                return null;
            }
        });
    }

    private void pruneOldDataAndExpire(String service, long nowMs) {
        long cutoff = nowMs - (WINDOW_MINUTES * 60 * 1000L);
        String[] metrics = {"request_count", "error_count", "latency_ms", "error_5xx_count", "error_429_count"};

        redis.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String metric : metrics) {
                    String key = METRICS_PREFIX + service + ":" + metric;
                    operations.opsForZSet().removeRangeByScore(key, 0, cutoff);
                    operations.expire(key, Duration.ofMinutes(WINDOW_MINUTES + 2));
                }
                return null;
            }
        });
    }

    public double getErrorRate(String service) {
        String rate = (String) redis.opsForHash().get(HEALTH_PREFIX + service, "error_rate");
        return rate != null ? Double.parseDouble(rate) : 0.0;
    }

    public long getCountInWindow(String service, String metric, int windowMinutes) {
        long windowStart = Instant.now().minusSeconds(windowMinutes * 60L).toEpochMilli();
        Long count = redis.opsForZSet().count(
                METRICS_PREFIX + service + ":" + metric, windowStart, Double.MAX_VALUE);
        return count != null ? count : 0L;
    }
}
