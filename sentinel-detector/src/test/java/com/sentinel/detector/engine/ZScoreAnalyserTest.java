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

class ZScoreAnalyserTest {

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zSetOps;
    private DetectionConfig config;
    private ZScoreAnalyser analyser;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zSetOps = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSetOps);

        config = new DetectionConfig();
        // Use tight thresholds for testing
        config.setZScoreP0Threshold(5.0);
        config.setZScoreP1Threshold(3.0);
        config.setZScoreP2Threshold(2.0);
        config.setWindowMinutes(5);
        config.setMinRequestsForDetection(3);

        analyser = new ZScoreAnalyser(redis, config);
    }

    @Test
    void testNoAnomaly_insufficientData() {
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Set.of("100:1000:0.1", "101:1001:0.2"));  // Only 2 values, need 3

        Optional<AnomalySignal> result = analyser.analyse("payment-service", "latency_ms");
        assertTrue(result.isEmpty());
    }

    @Test
    void testNoAnomaly_uniformValues() {
        // All latency = 100ms → stdDev = 0 → no anomaly
        Set<String> members = Set.of("100:1000:0.1", "100:1001:0.2", "100:1002:0.3",
                "100:1003:0.4", "100:1004:0.5");

        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(members);

        Optional<AnomalySignal> result = analyser.analyse("payment-service", "latency_ms");
        assertTrue(result.isEmpty());
    }

    @Test
    void testAnomaly_p1_detected() {
        // 19 normal values of 100ms and 1 outlier of 5000ms in members
        Set<String> members = new LinkedHashSet<>();
        for (int i = 0; i < 19; i++) {
            members.add("100:" + (1000 + i) + ":" + i);
        }
        members.add("5000:9999:0.99");  // outlier — 49 std deviations from mean

        // The latest is the outlier
        String latestMember = "5000:9999:0.99";

        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(members);
        when(zSetOps.reverseRangeByScore(anyString(), anyDouble(), anyDouble(), eq(0L), eq(1L)))
                .thenReturn(Set.of(latestMember));

        Optional<AnomalySignal> result = analyser.analyse("payment-service", "latency_ms");

        assertTrue(result.isPresent());
        AnomalySignal signal = result.get();
        assertEquals("payment-service", signal.getServiceName());
        assertEquals("latency_ms", signal.getMetricName());
        assertEquals(5000.0, signal.getActualValue(), 1.0);
        assertTrue(signal.getZScore() > config.getZScoreP1Threshold());
        assertNotNull(signal.getSeverity());
    }

    @Test
    void testAnomaly_returnsEmpty_whenNullMembers() {
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(null);
        Optional<AnomalySignal> result = analyser.analyse("order-service", "error_count");
        assertTrue(result.isEmpty());
    }

    @Test
    void testAnomaly_returnsEmpty_whenLatestMemberNull() {
        Set<String> members = new LinkedHashSet<>();
        for (int i = 0; i < 20; i++) members.add("100:" + i + ":" + Math.random());

        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(members);
        when(zSetOps.reverseRangeByScore(anyString(), anyDouble(), anyDouble(), eq(0L), eq(1L)))
                .thenReturn(Set.of());

        Optional<AnomalySignal> result = analyser.analyse("order-service", "latency_ms");
        assertTrue(result.isEmpty());
    }

    @Test
    void testAnomaly_skipsMalformedMembers() {
        Set<String> members = new LinkedHashSet<>();
        members.add("100:1000:0.1");
        members.add("malformed:1001:0.2");
        members.add("not-a-number:1002:0.3");

        // The parser filters NaNs, so we end up with 1 value, which is < 2. Returns empty.
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(members);

        Optional<AnomalySignal> result = analyser.analyse("payment-service", "latency_ms");
        assertTrue(result.isEmpty());
    }
}
