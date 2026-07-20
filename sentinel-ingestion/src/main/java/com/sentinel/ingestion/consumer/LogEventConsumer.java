package com.sentinel.ingestion.consumer;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.ingestion.entity.LogEvent;
import com.sentinel.ingestion.repository.LogEventRepository;
import com.sentinel.ingestion.service.RealTimeMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes log events from Kafka topic: log-events
 * For each event: saves to PostgreSQL + updates Redis real-time metrics
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventConsumer {

    private final LogEventRepository logEventRepository;
    private final RealTimeMetricsService metricsService;

    @KafkaListener(
            topics = "log-events",
            groupId = "sentinel-ingestion-group",
            concurrency = "3"         // 3 consumer threads for parallel processing
    )
    public void consume(
            @Payload LogEventDTO dto,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        try {
            // 1. Save to PostgreSQL
            LogEvent entity = toEntity(dto);
            logEventRepository.save(entity);

            // 2. Update Redis real-time metrics
            metricsService.updateMetrics(dto);

            // Log only ERRORs for visibility
            if (dto.getLogLevel().name().equals("ERROR")) {
                log.debug("ERROR event saved: service={} msg={}",
                        dto.getServiceName(), dto.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to process log event from partition={} offset={}: {}",
                    partition, offset, e.getMessage(), e);
            // In production: send to DLQ here
            // For now: log and continue (don't stop the consumer)
        }
    }

    private LogEvent toEntity(LogEventDTO dto) {
        return LogEvent.builder()
                .eventId(UUID.fromString(dto.getEventId()))
                .timestamp(dto.getTimestamp())
                .serviceName(dto.getServiceName())
                .logLevel(dto.getLogLevel())
                .message(dto.getMessage())
                .stackTrace(dto.getStackTrace())
                .requestId(dto.getRequestId())
                .latencyMs(dto.getLatencyMs())
                .statusCode(dto.getStatusCode())
                .host(dto.getHost())
                .metadata(dto.getMetadata())
                .build();
    }
}
