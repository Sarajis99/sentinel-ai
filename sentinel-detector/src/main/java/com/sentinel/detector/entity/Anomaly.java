package com.sentinel.detector.entity;

import com.sentinel.common.enums.AnomalyType;
import com.sentinel.common.enums.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the `anomalies` table in PostgreSQL.
 * Populated by sentinel-detector when an anomaly is detected.
 */
@Entity
@Table(name = "anomalies",
       indexes = {
           @Index(name = "idx_anomalies_service_ts", columnList = "service_name, detected_at DESC"),
           @Index(name = "idx_anomalies_status",     columnList = "status, severity")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anomaly_id", nullable = false, unique = true)
    @Builder.Default
    private UUID anomalyId = UUID.randomUUID();

    @Column(name = "detected_at", nullable = false)
    @Builder.Default
    private LocalDateTime detectedAt = LocalDateTime.now();

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", nullable = false, length = 50)
    private AnomalyType anomalyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Severity severity;

    @Column(name = "metric_name", length = 100)
    private String metricName;

    @Column(name = "expected_value")
    private Double expectedValue;

    @Column(name = "actual_value")
    private Double actualValue;

    @Column(name = "z_score")
    private Double zScore;

    @Column(name = "window_minutes")
    @Builder.Default
    private Integer windowMinutes = 5;

    /** DETECTED → ACKNOWLEDGED → RESOLVED */
    @Column(length = 20)
    @Builder.Default
    private String status = "DETECTED";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
