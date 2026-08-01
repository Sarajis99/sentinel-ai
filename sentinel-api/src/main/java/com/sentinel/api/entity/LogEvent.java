package com.sentinel.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the 'log_events' table.
 * sentinel-api uses this for data retention cleanup.
 */
@Entity
@Table(name = "log_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "service_name", length = 100)
    private String serviceName;

    @Column(name = "log_level", length = 10)
    private String logLevel;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "host", length = 100)
    private String host;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
