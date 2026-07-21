package com.sentinel.simulator.producer;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LogEventProducerTest {

    private KafkaTemplate<String, LogEventDTO> kafkaTemplate;
    private LogEventProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        producer = new LogEventProducer(kafkaTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendLogEvent() {
        LogEventDTO event = LogEventDTO.builder()
                .eventId("id-123")
                .timestamp(LocalDateTime.now())
                .serviceName("order-service")
                .logLevel(LogLevel.INFO)
                .message("Order processed")
                .requestId("req-abc")
                .latencyMs(120)
                .statusCode(200)
                .host("order-pod-1")
                .metadata(Map.of("env", "prod"))
                .build();

        CompletableFuture<SendResult<String, LogEventDTO>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(LogEventDTO.class))).thenReturn(future);

        producer.send(event);

        ArgumentCaptor<LogEventDTO> eventCaptor = ArgumentCaptor.forClass(LogEventDTO.class);
        verify(kafkaTemplate, times(1)).send(eq("log-events"), eq("order-service"), eventCaptor.capture());

        LogEventDTO sentEvent = eventCaptor.getValue();
        assertEquals("id-123", sentEvent.getEventId());
        assertEquals("order-service", sentEvent.getServiceName());
        assertEquals("Order processed", sentEvent.getMessage());
    }
    @Test
    @SuppressWarnings("unchecked")
    void testSendLogEventFailure() {
        LogEventDTO event = LogEventDTO.builder()
                .eventId("id-456")
                .serviceName("order-service")
                .build();

        CompletableFuture<SendResult<String, LogEventDTO>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        
        when(kafkaTemplate.send(any(String.class), any(String.class), any(LogEventDTO.class))).thenReturn(future);

        // The method logs the error but does not throw it
        producer.send(event);

        verify(kafkaTemplate, times(1)).send(eq("log-events"), eq("order-service"), any(LogEventDTO.class));
    }
}
