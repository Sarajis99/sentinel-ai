package com.sentinel.api.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RateLimiterService — provides lightweight rate-limiting for high-risk endpoints.
 * Protects LLM trigger and chaos injection routes from automated depletion / DoS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    // In-memory fallback if Redis is temporarily unavailable
    private final Map<String, WindowCounter> fallbackMap = new ConcurrentHashMap<>();

    /**
     * Checks if a client request is within the allowed rate limit window.
     *
     * @param actionKey     Identifier for the action (e.g. "test-llm", "retry-analysis")
     * @param clientIp      Client IP address
     * @param maxRequests   Maximum allowed requests in the time window
     * @param windowSeconds Time window in seconds
     * @return true if allowed, false if limit exceeded
     */
    public boolean isAllowed(String actionKey, String clientIp, int maxRequests, int windowSeconds) {
        String key = "ratelimit:" + actionKey + ":" + (clientIp != null ? clientIp : "unknown");

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > maxRequests) {
                log.warn("⚠️ Rate limit exceeded for key={}: {}/{} in {}s", key, count, maxRequests, windowSeconds);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Redis rate limiter unavailable ({}), using in-memory fallback", e.getMessage());
            return isAllowedFallback(key, maxRequests, windowSeconds);
        }
    }

    private boolean isAllowedFallback(String key, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;

        // Periodically purge stale entries
        fallbackMap.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > windowMillis * 2);

        WindowCounter counter = fallbackMap.compute(key, (k, existing) -> {
            if (existing == null || (now - existing.windowStart) > windowMillis) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.counter.incrementAndGet();
            return existing;
        });

        return counter != null && counter.counter.get() <= maxRequests;
    }

    private static class WindowCounter {
        final long windowStart;
        final AtomicInteger counter;

        WindowCounter(long windowStart, AtomicInteger counter) {
            this.windowStart = windowStart;
            this.counter = counter;
        }
    }
}
