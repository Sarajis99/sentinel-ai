package com.sentinel.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the 'anomalies' table.
 * sentinel-api uses this for read access and data retention cleanup.
 */
@Entity
@Table(name = "anomalies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anomaly_id", nullable = false, unique = true)
    private UUID anomalyId;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "anomaly_type", nullable = false, length = 50)
    private String anomalyType;

    @Column(name = "severity", nullable = false, length = 5)
    private String severity;

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

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "DETECTED";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
