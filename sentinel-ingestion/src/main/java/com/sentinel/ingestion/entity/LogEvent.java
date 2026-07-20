package com.sentinel.ingestion.entity;

import com.sentinel.common.enums.LogLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "log_events",
       indexes = {
           @Index(name = "idx_log_events_service_ts", columnList = "service_name, timestamp DESC"),
           @Index(name = "idx_log_events_level", columnList = "log_level, timestamp DESC")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_level", nullable = false, length = 10)
    private LogLevel logLevel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(length = 100)
    private String host;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
