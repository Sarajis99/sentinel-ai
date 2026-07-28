package com.sentinel.rca.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * RateLimiter — Redis-based token bucket rate limiter for LLM API calls.
 *
 * Prevents exceeding OpenRouter's free tier limit of 20 requests/minute.
 * Uses a sliding window counter in Redis with a 60-second TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_KEY = "rca:rate:llm:calls";

    @Value("${llm.rate-limit.max-per-minute:15}")
    private int maxPerMinute;

    /**
     * Check if an LLM call is allowed within the rate limit.
     * Uses atomic increment + TTL to implement a 1-minute sliding window.
     *
     * @return true if the call is allowed, false if rate limit is exceeded
     */
    public boolean tryAcquire() {
        try {
            Long count = redisTemplate.opsForValue().increment(RATE_LIMIT_KEY);

            if (count == null) {
                log.warn("Rate limiter Redis error — allowing request by default");
                return true;
            }

            // Set TTL only on first request in the window
            if (count == 1) {
                redisTemplate.expire(RATE_LIMIT_KEY, Duration.ofSeconds(60));
            }

            if (count > maxPerMinute) {
                log.warn("⚠️ LLM rate limit exceeded ({}/{} per min) — request blocked", count, maxPerMinute);
                return false;
            }

            log.debug("Rate limit: {}/{} requests this minute", count, maxPerMinute);
            return true;

        } catch (Exception e) {
            log.warn("Rate limiter check failed: {} — allowing request", e.getMessage());
            return true; // Fail open — don't block on Redis errors
        }
    }

    /**
     * Returns the current number of LLM calls in the last minute.
     */
    public long currentCount() {
        String value = redisTemplate.opsForValue().get(RATE_LIMIT_KEY);
        return value != null ? Long.parseLong(value) : 0;
    }
}
