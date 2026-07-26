package com.sentinel.detector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized thresholds for anomaly detection.
 * All values can be tuned in application.yml without code changes.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "detection")
public class DetectionConfig {

    private int windowMinutes = 5;

    // Z-Score thresholds
    private double zScoreP0Threshold = 5.0;    // 5 std deviations → P0
    private double zScoreP1Threshold = 3.0;    // 3 std deviations → P1
    private double zScoreP2Threshold = 2.0;    // 2 std deviations → P2

    // Error rate thresholds
    private double errorRateP0Threshold = 0.20;  // 20% → P0
    private double errorRateP1Threshold = 0.10;  // 10% → P1
    private double errorRateP2Threshold = 0.05;  // 5%  → P2

    // Minimum events needed before detection (avoids noise on cold start)
    private int minRequestsForDetection = 10;

    // Latency / Moving average deviation
    private double latencyDeviationThreshold = 0.50;  // 50% above moving avg → anomaly
    private int movingAverageLookbackMinutes = 15;

    // How often the detection engine runs (ms)
    private long detectorIntervalMs = 30_000;  // 30 seconds
}
