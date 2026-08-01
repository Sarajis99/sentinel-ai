package com.sentinel.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard summary statistics — displayed in the top metrics cards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {

    private long totalIncidents;
    private long openIncidents;
    private long resolvedIncidents;
    private long unknownIncidents;  // UNKNOWN root cause — needs manual triage

    private long totalAnomalies;
    private long totalLogEvents;

    private long p0Count;
    private long p1Count;
    private long p2Count;
    private long p3Count;

    private Double averageMttrSeconds;

    private boolean simulationActive;  // Is a simulation currently running?
}
