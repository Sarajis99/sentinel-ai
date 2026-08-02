package com.sentinel.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for incident data sent to the dashboard.
 * Maps from the Incident JPA entity but excludes internal fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDTO {

    private UUID incidentId;
    private String incidentNumber;
    private UUID anomalyId;
    private String title;
    private String severity;
    private String status;
    private String serviceName;

    // RCA fields
    private String rcaSummary;
    private String rootCause;
    private String impactAnalysis;
    private String suggestedFix;
    private String prevention;
    private Double confidence;

    // Timestamps
    private LocalDateTime detectedAt;
    private LocalDateTime analyzedAt;
    private LocalDateTime resolvedAt;
    private Integer mttrSeconds;

    // Context
    private String relatedLogs;
    private String similarIncidents;
}
