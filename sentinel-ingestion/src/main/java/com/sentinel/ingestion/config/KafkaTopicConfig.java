package com.sentinel.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic logEventsTopic() {
        return TopicBuilder.name("log-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic anomalyEventsTopic() {
        return TopicBuilder.name("anomaly-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic incidentEventsTopic() {
        return TopicBuilder.name("incident-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
