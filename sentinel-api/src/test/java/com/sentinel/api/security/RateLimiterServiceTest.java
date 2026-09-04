package com.sentinel.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(redisTemplate);
    }

    @Test
    void whenWithinLimit_allowsRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        boolean allowed = rateLimiterService.isAllowed("test-action", "127.0.0.1", 5, 60);

        assertTrue(allowed);
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    void whenExceedsLimit_blocksRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(6L);

        boolean allowed = rateLimiterService.isAllowed("test-action", "127.0.0.1", 5, 60);

        assertFalse(allowed);
    }

    @Test
    void whenRedisFails_usesInMemoryFallback() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        // First 3 requests should be allowed
        assertTrue(rateLimiterService.isAllowed("fallback-action", "192.168.1.1", 3, 60));
        assertTrue(rateLimiterService.isAllowed("fallback-action", "192.168.1.1", 3, 60));
        assertTrue(rateLimiterService.isAllowed("fallback-action", "192.168.1.1", 3, 60));

        // 4th request exceeds maxRequests=3, should be blocked
        assertFalse(rateLimiterService.isAllowed("fallback-action", "192.168.1.1", 3, 60));
    }

    @Test
    void whenXForwardedForPresent_extractsFirstIp() {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 70.41.3.18");

        String ip = rateLimiterService.getClientIp(request);
        org.junit.jupiter.api.Assertions.assertEquals("203.0.113.195", ip);
    }

    @Test
    void whenCfConnectingIpPresent_extractsIp() {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.1");

        String ip = rateLimiterService.getClientIp(request);
        org.junit.jupiter.api.Assertions.assertEquals("198.51.100.1", ip);
    }

    @Test
    void whenNoProxyHeaders_fallsBackToRemoteAddr() {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        String ip = rateLimiterService.getClientIp(request);
        org.junit.jupiter.api.Assertions.assertEquals("10.0.0.5", ip);
    }

    @Test
    void whenRequestNull_returnsUnknown() {
        String ip = rateLimiterService.getClientIp(null);
        org.junit.jupiter.api.Assertions.assertEquals("unknown", ip);
    }
}
