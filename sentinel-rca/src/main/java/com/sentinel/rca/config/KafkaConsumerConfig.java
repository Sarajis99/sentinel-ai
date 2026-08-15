package com.sentinel.rca.config;

import com.sentinel.common.dto.AnomalyDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for sentinel-rca.
 * Configures manual acknowledgment mode for reliable message processing.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, AnomalyDTO> consumerFactory(org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {
        JsonDeserializer<AnomalyDTO> deserializer = new JsonDeserializer<>(AnomalyDTO.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("com.sentinel.common.dto");
        deserializer.setUseTypeMapperForKey(true);

        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.entrySet().removeIf(e -> e.getKey().startsWith("spring.json"));
        
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual ACK
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);      // Process 10 at a time (LLM is slow)

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AnomalyDTO> kafkaListenerContainerFactory(org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {
        ConcurrentKafkaListenerContainerFactory<String, AnomalyDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(kafkaProperties));
        // Manual acknowledgment — commit only after RCA succeeds
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(2); // 2 parallel consumers (LLM calls can be slow)
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory(org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "sentinel-rca-retry-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> retryKafkaListenerContainerFactory(org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringConsumerFactory(kafkaProperties));
        return factory;
    }
}
