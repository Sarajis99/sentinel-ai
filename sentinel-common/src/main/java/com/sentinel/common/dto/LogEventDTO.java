package com.sentinel.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sentinel.common.enums.LogLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * LogEventDTO — the core event that flows through Kafka topic: log-events
 * Produced by sentinel-simulator, consumed by sentinel-ingestion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEventDTO {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    private String serviceName;      // e.g., "payment-service", "order-service"
    private LogLevel logLevel;       // ERROR, WARN, INFO, DEBUG
    private String message;          // Log message
    private String stackTrace;       // Present for ERROR logs
    private String requestId;        // Correlation ID for tracing
    private Integer latencyMs;       // API response time in ms
    private Integer statusCode;      // HTTP status code
    private String host;             // Hostname/pod name
    private Map<String, String> metadata;  // Extra context
}
