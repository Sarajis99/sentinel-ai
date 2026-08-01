package com.sentinel.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
