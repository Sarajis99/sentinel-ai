package com.sentinel.detector.service;

import com.sentinel.common.enums.Severity;
import com.sentinel.detector.config.DetectionConfig;
import com.sentinel.detector.engine.ErrorRateAnalyser;
import com.sentinel.detector.engine.MovingAverageAnalyser;
import com.sentinel.detector.engine.ZScoreAnalyser;
import com.sentinel.detector.model.AnomalySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnomalyDetectorTest {

    private ZScoreAnalyser zScoreAnalyser;
    private ErrorRateAnalyser errorRateAnalyser;
    private MovingAverageAnalyser movingAverageAnalyser;
    private AnomalyPublisher publisher;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private DetectionConfig config;
    private AnomalyDetector detector;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        zScoreAnalyser = mock(ZScoreAnalyser.class);
        errorRateAnalyser = mock(ErrorRateAnalyser.class);
        movingAverageAnalyser = mock(MovingAverageAnalyser.class);
        publisher = mock(AnomalyPublisher.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.hasKey(anyString())).thenReturn(false);  // No dedup by default

        config = new DetectionConfig();
        detector = new AnomalyDetector(zScoreAnalyser, errorRateAnalyser, movingAverageAnalyser,
                publisher, redis, config);
    }

    @Test
    void testRunDetectionForService_noAnomalies() {
        when(errorRateAnalyser.analyse(anyString())).thenReturn(Optional.empty());
        when(movingAverageAnalyser.analyse(anyString())).thenReturn(Optional.empty());
        when(zScoreAnalyser.analyse(anyString(), anyString())).thenReturn(Optional.empty());

        int count = detector.runDetectionForService("payment-service");

        assertEquals(0, count);
        verify(publisher, never()).publish(any());
    }

    @Test
    void testRunDetectionForService_errorRateAnomalyPublished() {
        AnomalySignal signal = buildSignal("payment-service", "error_rate");

        when(errorRateAnalyser.analyse("payment-service")).thenReturn(Optional.of(signal));
        when(movingAverageAnalyser.analyse(anyString())).thenReturn(Optional.empty());
        when(zScoreAnalyser.analyse(anyString(), anyString())).thenReturn(Optional.empty());

        int count = detector.runDetectionForService("payment-service");

        assertEquals(1, count);
        verify(publisher, times(1)).publish(signal);
    }

    @Test
    void testRunDetectionForService_latencyAnomalyPublished() {
        AnomalySignal signal = buildSignal("order-service", "latency_ms");

        when(errorRateAnalyser.analyse(anyString())).thenReturn(Optional.empty());
        when(movingAverageAnalyser.analyse("order-service")).thenReturn(Optional.of(signal));
        when(zScoreAnalyser.analyse(anyString(), anyString())).thenReturn(Optional.empty());

        int count = detector.runDetectionForService("order-service");

        assertEquals(1, count);
        verify(publisher, times(1)).publish(signal);
    }

    @Test
    void testRunDetectionForService_deduplicated_noPublish() {
        AnomalySignal signal = buildSignal("payment-service", "error_rate");

        when(errorRateAnalyser.analyse("payment-service")).thenReturn(Optional.of(signal));
        when(movingAverageAnalyser.analyse(anyString())).thenReturn(Optional.empty());
        when(zScoreAnalyser.analyse(anyString(), anyString())).thenReturn(Optional.empty());
        // Simulate dedup key already exists
        when(redis.hasKey("dedup:anomaly:payment-service:error_rate")).thenReturn(true);

        int count = detector.runDetectionForService("payment-service");

        assertEquals(0, count);
        verify(publisher, never()).publish(any());
    }

    @Test
    void testRunDetectionForService_multipleAnomalies() {
        AnomalySignal errorSignal = buildSignal("payment-service", "error_rate");
        AnomalySignal latencySignal = buildSignal("payment-service", "latency_ms");
        AnomalySignal zScoreSignal = buildSignal("payment-service", "error_count");

        when(errorRateAnalyser.analyse("payment-service")).thenReturn(Optional.of(errorSignal));
        when(movingAverageAnalyser.analyse("payment-service")).thenReturn(Optional.of(latencySignal));
        when(zScoreAnalyser.analyse(eq("payment-service"), anyString()))
                .thenReturn(Optional.of(zScoreSignal));

        int count = detector.runDetectionForService("payment-service");

        // error_rate + latency_moving_avg + 4 z-score metrics = 6
        assertEquals(6, count);
    }

    @Test
    void testRunDetection_multipleServices() {
        AnomalySignal signal1 = buildSignal("payment-service", "error_rate");
        AnomalySignal signal2 = buildSignal("order-service", "latency_moving_avg");

        when(errorRateAnalyser.analyse("payment-service")).thenReturn(Optional.of(signal1));
        when(movingAverageAnalyser.analyse("order-service")).thenReturn(Optional.of(signal2));
        when(errorRateAnalyser.analyse(argThat(s -> !s.equals("payment-service")))).thenReturn(Optional.empty());
        when(movingAverageAnalyser.analyse(argThat(s -> !s.equals("order-service")))).thenReturn(Optional.empty());
        when(zScoreAnalyser.analyse(anyString(), anyString())).thenReturn(Optional.empty());

        detector.runDetection();

        verify(publisher, times(1)).publish(signal1);
        verify(publisher, times(1)).publish(signal2);
        
        // Verifies dedup is set for both
        verify(valueOps, times(1)).set(eq("dedup:anomaly:payment-service:error_rate"), eq("1"));
        verify(valueOps, times(1)).set(eq("dedup:anomaly:order-service:latency_moving_avg"), eq("1"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private AnomalySignal buildSignal(String service, String metric) {
        return AnomalySignal.builder()
                .serviceName(service)
                .metricName(metric)
                .severity(Severity.P1)
                .expectedValue(0.02)
                .actualValue(0.20)
                .zScore(3.5)
                .windowMinutes(5)
                .build();
    }
}
