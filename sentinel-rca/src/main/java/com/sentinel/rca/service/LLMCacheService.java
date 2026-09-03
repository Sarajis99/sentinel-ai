package com.sentinel.rca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * LLMCacheService — Redis-backed cache for LLM responses.
 *
 * Identical anomalies (same service + same type) within 1 hour
 * will reuse the cached RCA instead of calling the LLM again.
 * This prevents redundant API calls and speeds up responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "rca:cache:";

    @Value("${llm.cache.ttl-seconds:3600}")
    private long cacheTtlSeconds;

    @Value("${llm.cache.enabled:true}")
    private boolean cacheEnabled;

    /**
     * Build a cache key from the anomaly characteristics.
     * Same service + same anomaly type = reuse existing RCA.
     */
    public String buildCacheKey(String serviceName, String anomalyType, String metricName) {
        return CACHE_PREFIX + serviceName + ":" + anomalyType + ":" + metricName;
    }

    /**
     * Try to get a cached RCA JSON string.
     */
    public Optional<String> get(String cacheKey) {
        if (!cacheEnabled) return Optional.empty();

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("⚡ Cache HIT for key: {}", cacheKey);
                return Optional.of(cached);
            }
            log.debug("Cache MISS for key: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Redis cache read failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Store an RCA JSON string in the cache.
     */
    public void put(String cacheKey, String rcaJson) {
        if (!cacheEnabled) return;

        try {
            redisTemplate.opsForValue().set(cacheKey, rcaJson, Duration.ofSeconds(cacheTtlSeconds));
            log.info("💾 Cached RCA response for key: {} (TTL={}s)", cacheKey, cacheTtlSeconds);
        } catch (Exception e) {
            log.warn("Redis cache write failed: {}", e.getMessage());
        }
    }

    /**
     * Evict a cached RCA key from Redis.
     */
    public void evict(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
            log.info("🗑️ Evicted cache key: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Redis cache evict failed: {}", e.getMessage());
        }
    }
}
