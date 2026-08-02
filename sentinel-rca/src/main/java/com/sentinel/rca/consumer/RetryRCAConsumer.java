package com.sentinel.rca.consumer;

import com.sentinel.rca.service.RCAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryRCAConsumer {

    private final RCAService rcaService;

    @KafkaListener(
        topics = "rca-retry-events", 
        groupId = "sentinel-rca-retry-group", 
        containerFactory = "retryKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        String incidentIdStr = record.value();
        log.info("📥 Received retry event for incident: {}", incidentIdStr);
        try {
            UUID incidentId = UUID.fromString(incidentIdStr);
            rcaService.retryRCA(incidentId);
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid UUID format for incident ID: {}", incidentIdStr, e);
        } catch (Exception e) {
            log.error("❌ Failed to process retry event for incident: {}", incidentIdStr, e);
        }
    }
}
