package com.sentinel.rca.consumer;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.rca.service.RCAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * AnomalyEventConsumer — Kafka listener for the 'anomaly-events' topic.
 *
 * Receives AnomalyDTO messages published by sentinel-detector
 * and hands them off to RCAService for AI-powered root cause analysis.
 *
 * Uses manual acknowledgment to ensure the message is not lost
 * if the RCA process fails midway.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyEventConsumer {

    private final RCAService rcaService;

    @KafkaListener(
            topics = "anomaly-events",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, AnomalyDTO> record, Acknowledgment ack) {
        AnomalyDTO anomaly = record.value();

        log.info("📥 Received anomaly event: service={} type={} severity={} offset={}",
                anomaly.getServiceName(),
                anomaly.getAnomalyType(),
                anomaly.getSeverity(),
                record.offset()
        );

        try {
            rcaService.performRCA(anomaly);
            ack.acknowledge(); // Commit offset only after successful processing
        } catch (Exception e) {
            log.error("❌ Failed to process anomaly event: anomalyId={} error={}",
                    anomaly.getAnomalyId(), e.getMessage(), e);
            // Do NOT acknowledge — message will be re-delivered on restart
            // In production, send to DLQ after N retries
        }
    }
}
