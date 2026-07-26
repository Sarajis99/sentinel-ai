package com.sentinel.detector.engine;

import com.sentinel.common.enums.Severity;
import com.sentinel.detector.config.DetectionConfig;
import com.sentinel.detector.model.AnomalySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErrorRateAnalyserTest {

    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private HashOperations<String, Object, Object> hashOps;
    private DetectionConfig config;
    private ErrorRateAnalyser analyser;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);

        config = new DetectionConfig();
        config.setMinRequestsForDetection(10);
        config.setErrorRateP0Threshold(0.20);
        config.setErrorRateP1Threshold(0.10);
        config.setErrorRateP2Threshold(0.05);
        config.setWindowMinutes(5);

        analyser = new ErrorRateAnalyser(redis, config);
    }

    @Test
    void testNoAnomaly_belowThreshold() {
        when(hashOps.get("health:payment-service", "error_rate")).thenReturn("0.02");
        when(hashOps.get("health:payment-service", "request_count")).thenReturn("100");

        Optional<AnomalySignal> result = analyser.analyse("payment-service");
        assertTrue(result.isEmpty());
    }

    @Test
    void testAnomaly_p2_detected() {
        when(hashOps.get("health:payment-service", "error_rate")).thenReturn("0.07");
        when(hashOps.get("health:payment-service", "request_count")).thenReturn("100");

        Optional<AnomalySignal> result = analyser.analyse("payment-service");
        assertTrue(result.isPresent());
        assertEquals(Severity.P2, result.get().getSeverity());
        assertEquals("error_rate", result.get().getMetricName());
        assertEquals(0.07, result.get().getActualValue());
    }

    @Test
    void testAnomaly_p1_detected() {
        when(hashOps.get("health:order-service", "error_rate")).thenReturn("0.15");
        when(hashOps.get("health:order-service", "request_count")).thenReturn("50");

        Optional<AnomalySignal> result = analyser.analyse("order-service");
        assertTrue(result.isPresent());
        assertEquals(Severity.P1, result.get().getSeverity());
    }

    @Test
    void testAnomaly_p0_detected() {
        when(hashOps.get("health:order-service", "error_rate")).thenReturn("0.30");
        when(hashOps.get("health:order-service", "request_count")).thenReturn("50");

        Optional<AnomalySignal> result = analyser.analyse("order-service");
        assertTrue(result.isPresent());
        assertEquals(Severity.P0, result.get().getSeverity());
    }

    @Test
    void testNoAnomaly_nullValues() {
        when(hashOps.get(anyString(), eq("error_rate"))).thenReturn(null);
        when(hashOps.get(anyString(), eq("request_count"))).thenReturn(null);

        Optional<AnomalySignal> result = analyser.analyse("payment-service");
        assertTrue(result.isEmpty());
    }

    @Test
    void testNoAnomaly_insufficientRequests() {
        when(hashOps.get("health:payment-service", "error_rate")).thenReturn("0.50");
        when(hashOps.get("health:payment-service", "request_count")).thenReturn("5"); // less than min=10

        Optional<AnomalySignal> result = analyser.analyse("payment-service");
        assertTrue(result.isEmpty());
    }

    @Test
    void testNoAnomaly_invalidNumberFormat() {
        when(hashOps.get(anyString(), eq("error_rate"))).thenReturn("not-a-number");
        when(hashOps.get(anyString(), eq("request_count"))).thenReturn("100");

        Optional<AnomalySignal> result = analyser.analyse("payment-service");
        assertTrue(result.isEmpty());
    }
}
