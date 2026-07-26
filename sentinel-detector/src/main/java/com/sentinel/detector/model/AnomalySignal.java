package com.sentinel.detector.model;

import com.sentinel.common.enums.Severity;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a detected anomaly signal from one of the analysers.
 * The orchestrator converts this into an AnomalyDTO and publishes to Kafka.
 */
@Data
@Builder
public class AnomalySignal {
    private String serviceName;
    private String metricName;
    private double expectedValue;
    private double actualValue;
    private double zScore;
    private Severity severity;
    private String description;
    private int windowMinutes;
}
