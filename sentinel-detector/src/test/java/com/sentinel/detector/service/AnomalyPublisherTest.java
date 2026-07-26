package com.sentinel.detector.service;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.common.enums.AnomalyType;
import com.sentinel.common.enums.Severity;
import com.sentinel.detector.entity.Anomaly;
import com.sentinel.detector.model.AnomalySignal;
import com.sentinel.detector.repository.AnomalyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnomalyPublisherTest {

    private KafkaTemplate<String, AnomalyDTO> kafkaTemplate;
    private AnomalyRepository anomalyRepository;
    private AnomalyPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        anomalyRepository = mock(AnomalyRepository.class);
        publisher = new AnomalyPublisher(kafkaTemplate, anomalyRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublish_success() {
        AnomalySignal signal = AnomalySignal.builder()
                .serviceName("payment-service")
                .metricName("error_rate")
                .expectedValue(0.02)
                .actualValue(0.25)
                .zScore(3.5)
                .severity(Severity.P0)
                .description("Error rate spike")
                .windowMinutes(5)
                .build();

        CompletableFuture<SendResult<String, AnomalyDTO>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any(AnomalyDTO.class))).thenReturn(future);

        publisher.publish(signal);

        // Verify saved to PostgreSQL
        ArgumentCaptor<Anomaly> dbCaptor = ArgumentCaptor.forClass(Anomaly.class);
        verify(anomalyRepository, times(1)).save(dbCaptor.capture());
        Anomaly saved = dbCaptor.getValue();
        assertEquals("payment-service", saved.getServiceName());
        assertEquals(Severity.P0, saved.getSeverity());
        assertEquals("DETECTED", saved.getStatus());

        // Verify published to Kafka
        ArgumentCaptor<AnomalyDTO> captor = ArgumentCaptor.forClass(AnomalyDTO.class);
        verify(kafkaTemplate, times(1)).send(eq("anomaly-events"), eq("payment-service"), captor.capture());

        AnomalyDTO published = captor.getValue();
        assertEquals("payment-service", published.getServiceName());
        assertEquals(Severity.P0, published.getSeverity());
        assertEquals(AnomalyType.ERROR_SPIKE, published.getAnomalyType());
        assertEquals("error_rate", published.getMetricName());
        assertEquals(0.02, published.getExpectedValue());
        assertEquals(0.25, published.getActualValue());
        assertEquals(3.5, published.getZScore());
        assertEquals(5, published.getWindowMinutes());
        assertNotNull(published.getDetectedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublish_latencyMapsToLatencySurge() {
        AnomalySignal signal = AnomalySignal.builder()
                .serviceName("order-service")
                .metricName("latency_ms")
                .severity(Severity.P1)
                .build();

        CompletableFuture<SendResult<String, AnomalyDTO>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        publisher.publish(signal);

        ArgumentCaptor<AnomalyDTO> captor = ArgumentCaptor.forClass(AnomalyDTO.class);
        verify(kafkaTemplate).send(eq("anomaly-events"), eq("order-service"), captor.capture());
        assertEquals(AnomalyType.LATENCY_SURGE, captor.getValue().getAnomalyType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublish_requestCountMapsToThroughputDrop() {
        AnomalySignal signal = AnomalySignal.builder()
                .serviceName("inventory-service")
                .metricName("request_count")
                .severity(Severity.P2)
                .build();

        CompletableFuture<SendResult<String, AnomalyDTO>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        publisher.publish(signal);

        ArgumentCaptor<AnomalyDTO> captor = ArgumentCaptor.forClass(AnomalyDTO.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        assertEquals(AnomalyType.THROUGHPUT_DROP, captor.getValue().getAnomalyType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublish_kafkaFailure_doesNotThrow() {
        AnomalySignal signal = AnomalySignal.builder()
                .serviceName("payment-service")
                .metricName("error_rate")
                .severity(Severity.P1)
                .build();

        CompletableFuture<SendResult<String, AnomalyDTO>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        // Should not throw
        assertDoesNotThrow(() -> publisher.publish(signal));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublish_unknownMetricMapsToAvailabilityDrop() {
        AnomalySignal signal = AnomalySignal.builder()
                .serviceName("user-service")
                .metricName("unknown_metric")
                .severity(Severity.P2)
                .build();

        CompletableFuture<SendResult<String, AnomalyDTO>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        publisher.publish(signal);

        ArgumentCaptor<AnomalyDTO> captor = ArgumentCaptor.forClass(AnomalyDTO.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        assertEquals(AnomalyType.AVAILABILITY_DROP, captor.getValue().getAnomalyType());
    }
}
