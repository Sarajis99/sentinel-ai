package com.sentinel.detector.engine;

import com.sentinel.common.enums.Severity;
import com.sentinel.detector.config.DetectionConfig;
import com.sentinel.detector.model.AnomalySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MovingAverageAnalyserTest {

    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> zSetOps;
    private DetectionConfig config;
    private MovingAverageAnalyser analyser;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zSetOps = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSetOps);

        config = new DetectionConfig();
        config.setWindowMinutes(5);
        config.setMovingAverageLookbackMinutes(15);
        config.setLatencyDeviationThreshold(0.50);

        analyser = new MovingAverageAnalyser(redis, config);
    }

    @Test
    void testNoAnomaly_noData() {
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(null);
        Optional<AnomalySignal> result = analyser.analyse("payment-service");
        assertTrue(result.isEmpty());
    }

    @Test
    void testNoAnomaly_belowThreshold() {
        // Short window (recent): 110ms avg — slightly above long window 100ms
        // Deviation = 10% < 50% threshold
        Set<String> shortWindow = createMembersWithValue(100, "110");
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(shortWindow);

        Optional<AnomalySignal> result = analyser.analyse("payment-service");
        // Both windows return same data → 0% deviation
        assertTrue(result.isEmpty());
    }

    @Test
    void testAnomaly_detected_p2() {
        // Short window: 200ms, Long window: 100ms → 100% deviation > 50% threshold
        Set<String> shortWindow = createMembersWithValue(5, "200");
        Set<String> longWindow  = createMembersWithValue(20, "100");

        // First call returns short window, second call returns long window
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(shortWindow)  // short window (5-min)
                .thenReturn(longWindow);  // long window (15-min)

        Optional<AnomalySignal> result = analyser.analyse("payment-service");

        assertTrue(result.isPresent());
        AnomalySignal signal = result.get();
        assertEquals("payment-service", signal.getServiceName());
        assertEquals("latency_ms", signal.getMetricName());
        assertEquals(200.0, signal.getActualValue(), 1.0);
        assertEquals(100.0, signal.getExpectedValue(), 1.0);
        assertNotNull(signal.getSeverity());
    }

    @Test
    void testAnomaly_detected_p1_100percent_deviation() {
        // Short window: 300ms, Long window: 100ms → 200% deviation → P1
        Set<String> shortWindow = createMembersWithValue(5, "300");
        Set<String> longWindow  = createMembersWithValue(20, "100");

        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(shortWindow)
                .thenReturn(longWindow);

        Optional<AnomalySignal> result = analyser.analyse("payment-service");

        assertTrue(result.isPresent());
        assertEquals(Severity.P1, result.get().getSeverity());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Set<String> createMembersWithValue(int count, String value) {
        Set<String> members = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            members.add(value + ":" + (1000 + i) + ":" + Math.random());
        }
        return members;
    }
}
