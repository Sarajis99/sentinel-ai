package com.sentinel.rca.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the 'incidents' table.
 * Owned by sentinel-rca — populated after LLM RCA analysis.
 * Linked to the 'anomalies' table via anomaly_id foreign key.
 */
@Entity
@Table(name = "incidents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, unique = true)
    private UUID incidentId;

    @Column(name = "incident_number", unique = true, length = 20)
    private String incidentNumber;

    @Column(name = "anomaly_id")
    private UUID anomalyId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "severity", nullable = false, length = 5)
    private String severity;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    // ─── LLM-generated RCA fields ────────────────────────────────────────────

    @Column(name = "rca_summary", columnDefinition = "TEXT")
    private String rcaSummary;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "impact_analysis", columnDefinition = "TEXT")
    private String impactAnalysis;

    @Column(name = "suggested_fix", columnDefinition = "TEXT")
    private String suggestedFix;

    @Column(name = "prevention", columnDefinition = "TEXT")
    private String prevention;

    @Column(name = "confidence")
    private Double confidence;

    // Removed embedding field: requires hibernate-vector to map float[] to pgvector 'vector'

    // ─── Timestamps ──────────────────────────────────────────────────────────

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "mttr_seconds")
    private Integer mttrSeconds;

    // ─── Context ─────────────────────────────────────────────────────────────

    @Column(name = "related_logs", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private com.fasterxml.jackson.databind.JsonNode relatedLogs;

    @Column(name = "similar_incidents", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private com.fasterxml.jackson.databind.JsonNode similarIncidents;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
