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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consumes log events from Kafka topic: log-events
 * For each batch: saves to PostgreSQL + updates Redis real-time metrics
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
            @Payload List<LogEventDTO> dtos) {

        try {
            if (dtos == null || dtos.isEmpty()) return;
            
            // 1. Bulk Save to PostgreSQL (Requires spring.kafka.listener.type=batch)
            List<LogEvent> entities = dtos.stream()
                    .map(this::toEntity)
                    .collect(Collectors.toList());
            logEventRepository.saveAll(entities);

            // 2. Update Redis real-time metrics for the entire batch
            metricsService.updateMetricsBatch(dtos);

            // 3. Log errors for visibility
            for (LogEventDTO dto : dtos) {
                // Log only ERRORs for visibility
                if ("ERROR".equals(dto.getLogLevel().name())) {
                    log.debug("ERROR event saved: service={} msg={}",
                            dto.getServiceName(), dto.getMessage());
                }
            }

            log.info("r? Ingested batch of {} log events.", dtos.size());

        } catch (Exception e) {
            log.error("Failed to process batch of {} log events: {}", dtos.size(), e.getMessage(), e);
            // In production: send to DLQ here
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
