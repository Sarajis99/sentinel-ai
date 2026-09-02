package com.sentinel.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.api.service.WakeUpService;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HealthController — System health checks for the dashboard.
 *
 * Reports connectivity status of all infrastructure components:
 *   - PostgreSQL database
 *   - Redis cache
 *   - Overall system status (GREEN/YELLOW/RED)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final WakeUpService wakeUpService;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @GetMapping("/services-status")
    public Map<String, String> getServicesStatus() {
        return wakeUpService.wakeAllServices();
    }

    @GetMapping("/kafka-keepalive")
    public ResponseEntity<Map<String, String>> kafkaKeepAlive() {
        try {
            // Wake up other services so they don't sleep forever
            wakeUpService.wakeAllServices();
            
            // Send a dummy message to a heartbeat topic to keep Aiven Kafka alive
            kafkaTemplate.send("heartbeat", "keep-alive", java.time.Instant.now().toString());
            
            log.info("Sent keep-alive ping to Kafka and woke up other services.");
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Keep-alive ping sent to Kafka"));
        } catch (Exception e) {
            log.error("Failed to send Kafka keep-alive ping", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/health
     * Returns health status of all infrastructure components.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();

        // Check PostgreSQL
        boolean pgHealthy = checkPostgres();
        health.put("postgres", Map.of(
                "status", pgHealthy ? "UP" : "DOWN",
                "message", pgHealthy ? "Connected" : "Connection failed"
        ));

        // Check Redis
        boolean redisHealthy = checkRedis();
        health.put("redis", Map.of(
                "status", redisHealthy ? "UP" : "DOWN",
                "message", redisHealthy ? "Connected" : "Connection failed"
        ));

        // Overall status
        String overall;
        if (pgHealthy && redisHealthy) {
            overall = "GREEN";
        } else if (pgHealthy || redisHealthy) {
            overall = "YELLOW";
        } else {
            overall = "RED";
        }

        health.put("overall", overall);
        
        // Add services health from Redis
        java.util.List<String> monitoredServices = java.util.List.of(
                "payment-service",
                "order-service",
                "inventory-service",
                "notification-service",
                "user-service"
        );
        Map<String, Object> servicesHealth = new LinkedHashMap<>();
        
        for (String s : monitoredServices) {
            String key = "health:" + s;
            Map<Object, Object> redisData = redisTemplate.opsForHash().entries(key);
            
            if (redisData == null || redisData.isEmpty()) {
                servicesHealth.put(s, Map.of(
                        "status", "IDLE",
                        "errorRate", 0.0,
                        "requestCount", 0,
                        "lastUpdated", "No data"
                ));
            } else {
                double errorRate = 0.0;
                long requestCount = 0;
                double p99Latency = 0.0;
                String lastUpdated = "Unknown";
                
                try {
                    errorRate = Double.parseDouble((String) redisData.getOrDefault("error_rate", "0.0"));
                    requestCount = Long.parseLong((String) redisData.getOrDefault("request_count", "0"));
                    p99Latency = Double.parseDouble((String) redisData.getOrDefault("p99_latency", "0.0"));
                    lastUpdated = (String) redisData.getOrDefault("last_updated", "Unknown");
                } catch (Exception e) {
                    log.warn("Failed to parse health data for {}: {}", s, e.getMessage());
                }
                
                String status = "HEALTHY";
                if (errorRate > 0.30 || p99Latency > 500) {
                    status = "CRITICAL";
                } else if (errorRate > 0.15 || p99Latency > 250) {
                    status = "DEGRADED";
                } else if (errorRate >= 0.05 || p99Latency > 150) {
                    status = "WARNING";
                }
                
                servicesHealth.put(s, Map.of(
                        "status", status,
                        "errorRate", errorRate,
                        "p99Latency", p99Latency,
                        "requestCount", requestCount,
                        "lastUpdated", lastUpdated
                ));
            }
        }
        health.put("services", servicesHealth);

        health.put("service", "sentinel-api");
        health.put("timestamp", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(health);
    }

    private boolean checkPostgres() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            log.warn("PostgreSQL health check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkRedis() {
        try {
            String result = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }
}
