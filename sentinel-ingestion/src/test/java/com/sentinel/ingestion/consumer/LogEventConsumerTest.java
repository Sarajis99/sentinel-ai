package com.sentinel.ingestion.consumer;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import com.sentinel.ingestion.entity.LogEvent;
import com.sentinel.ingestion.repository.LogEventRepository;
import com.sentinel.ingestion.service.RealTimeMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LogEventConsumerTest {

    private LogEventRepository repository;
    private RealTimeMetricsService metricsService;
    private LogEventConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(LogEventRepository.class);
        metricsService = mock(RealTimeMetricsService.class);
        consumer = new LogEventConsumer(repository, metricsService);
    }

    @Test
    void testConsumeLogEventSuccess() {
        LogEventDTO dto = LogEventDTO.builder()
                .eventId("da4b10b0-f3c5-42bc-9e1a-5a302449ac60") // Valid UUID
                .timestamp(LocalDateTime.now())
                .serviceName("payment-service")
                .logLevel(LogLevel.ERROR)
                .message("Payment failed due to timeout")
                .stackTrace("java.lang.RuntimeException")
                .requestId("req-123")
                .latencyMs(250)
                .statusCode(500)
                .host("payment-pod-1")
                .metadata(Map.of("env", "prod"))
                .build();

        consumer.consume(dto, 0, 100L);

        // Verify entity mapping and saving
        ArgumentCaptor<LogEvent> entityCaptor = ArgumentCaptor.forClass(LogEvent.class);
        verify(repository, times(1)).save(entityCaptor.capture());
        
        LogEvent savedEntity = entityCaptor.getValue();
        assertEquals("da4b10b0-f3c5-42bc-9e1a-5a302449ac60", savedEntity.getEventId().toString());
        assertEquals("payment-service", savedEntity.getServiceName());
        assertEquals(LogLevel.ERROR, savedEntity.getLogLevel());
        assertEquals("Payment failed due to timeout", savedEntity.getMessage());
        assertEquals("java.lang.RuntimeException", savedEntity.getStackTrace());
        assertEquals("req-123", savedEntity.getRequestId());
        assertEquals(250, savedEntity.getLatencyMs());
        assertEquals(500, savedEntity.getStatusCode());
        assertEquals("payment-pod-1", savedEntity.getHost());
        assertNotNull(savedEntity.getMetadata());

        // Verify metrics update
        verify(metricsService, times(1)).updateMetrics(dto);
    }

    @Test
    void testConsumeLogEventExceptionHandled() {
        LogEventDTO dto = LogEventDTO.builder()
                .eventId("da4b10b0-f3c5-42bc-9e1a-5a302449ac60") // Valid UUID
                .serviceName("order-service")
                .logLevel(LogLevel.INFO)
                .build();

        // Force a runtime exception during saving
        doThrow(new RuntimeException("DB Outage")).when(repository).save(any(LogEvent.class));

        // It should catch the exception and still run without throwing
        consumer.consume(dto, 1, 200L);

        verify(repository, times(1)).save(any(LogEvent.class));
        verify(metricsService, never()).updateMetrics(any());
    }

    @Test
    void testConsumeLogEventSuccessInfoLevel() {
        LogEventDTO dto = LogEventDTO.builder()
                .eventId("da4b10b0-f3c5-42bc-9e1a-5a302449ac60")
                .timestamp(LocalDateTime.now())
                .serviceName("payment-service")
                .logLevel(LogLevel.INFO)
                .message("Payment processed")
                .build();

        consumer.consume(dto, 0, 101L);

        verify(repository, times(1)).save(any(LogEvent.class));
        verify(metricsService, times(1)).updateMetrics(dto);
    }
}
