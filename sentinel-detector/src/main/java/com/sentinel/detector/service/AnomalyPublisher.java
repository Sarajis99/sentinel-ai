package com.sentinel.detector.service;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.common.enums.AnomalyType;
import com.sentinel.detector.entity.Anomaly;
import com.sentinel.detector.model.AnomalySignal;
import com.sentinel.detector.repository.AnomalyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes detected anomalies to:
 *  1. PostgreSQL anomalies table (persistent record)
 *  2. Kafka topic: anomaly-events (for downstream consumers like sentinel-rca)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyPublisher {

    private final KafkaTemplate<String, AnomalyDTO> anomalyKafkaTemplate;
    private final AnomalyRepository anomalyRepository;

    private static final String TOPIC = "anomaly-events";

    /**
     * Saves anomaly to PostgreSQL and publishes to Kafka.
     * Key = serviceName (ensures ordering per service).
     */
    public void publish(AnomalySignal signal) {
        AnomalyType type = mapToType(signal.getMetricName());
        LocalDateTime now = LocalDateTime.now();
        UUID anomalyId = UUID.randomUUID();

        // 1. Save to PostgreSQL anomalies table
        try {
            Anomaly entity = Anomaly.builder()
                    .anomalyId(anomalyId)
                    .detectedAt(now)
                    .serviceName(signal.getServiceName())
                    .anomalyType(type)
                    .severity(signal.getSeverity())
                    .metricName(signal.getMetricName())
                    .expectedValue(signal.getExpectedValue())
                    .actualValue(signal.getActualValue())
                    .zScore(signal.getZScore())
                    .windowMinutes(signal.getWindowMinutes())
                    .status("DETECTED")
                    .build();

            anomalyRepository.save(entity);
            log.info("💾 Anomaly saved to PostgreSQL: service={} severity={} metric={}",
                    signal.getServiceName(), signal.getSeverity(), signal.getMetricName());

        } catch (Exception e) {
            log.error("Failed to save anomaly to PostgreSQL: {}", e.getMessage(), e);
            // Continue and still try to publish to Kafka
        }

        // 2. Build DTO and publish to Kafka
        AnomalyDTO dto = AnomalyDTO.builder()
                .anomalyId(anomalyId.toString())
                .detectedAt(now)
                .serviceName(signal.getServiceName())
                .anomalyType(type)
                .severity(signal.getSeverity())
                .metricName(signal.getMetricName())
                .expectedValue(signal.getExpectedValue())
                .actualValue(signal.getActualValue())
                .zScore(signal.getZScore())
                .windowMinutes(signal.getWindowMinutes())
                .build();

        anomalyKafkaTemplate.send(TOPIC, signal.getServiceName(), dto)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish anomaly to Kafka for service={}: {}",
                                signal.getServiceName(), ex.getMessage());
                    } else {
                        log.info("✅ Anomaly published → Kafka topic={} service={} severity={} metric={}",
                                TOPIC, signal.getServiceName(), signal.getSeverity(), signal.getMetricName());
                    }
                });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private AnomalyType mapToType(String metricName) {
        if (metricName == null) return AnomalyType.ERROR_SPIKE;
        return switch (metricName) {
            case "error_rate", "error_count", "error_5xx_count" -> AnomalyType.ERROR_SPIKE;
            case "latency_ms"                                    -> AnomalyType.LATENCY_SURGE;
            case "request_count"                                 -> AnomalyType.THROUGHPUT_DROP;
            default                                              -> AnomalyType.AVAILABILITY_DROP;
        };
    }
}
