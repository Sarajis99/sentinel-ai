package com.sentinel.simulator.producer;

import com.sentinel.common.dto.LogEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventProducer {

    private final KafkaTemplate<String, LogEventDTO> kafkaTemplate;

    private static final String TOPIC = "log-events";

    /**
     * Send a single log event to Kafka.
     * Key = serviceName (ensures logs from same service go to same partition)
     */
    public void send(LogEventDTO event) {
        CompletableFuture<SendResult<String, LogEventDTO>> future =
                kafkaTemplate.send(TOPIC, event.getServiceName(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send log event: {}", ex.getMessage());
            }
            // Don't log success — too noisy for high-throughput scenario
        });
    }
}
