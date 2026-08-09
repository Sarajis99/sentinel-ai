package com.sentinel.rca.consumer;

import com.sentinel.rca.service.RCAService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryRCAConsumerTest {

    @Mock
    private RCAService rcaService;

    @InjectMocks
    private RetryRCAConsumer retryRCAConsumer;

    @Test
    void testConsume_ValidUUID() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", id.toString());

        retryRCAConsumer.consume(record);

        verify(rcaService).retryRCA(id);
    }

    @Test
    void testConsume_ValidUUIDWithQuotes() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", "\"" + id.toString() + "\"");

        retryRCAConsumer.consume(record);

        verify(rcaService).retryRCA(id);
    }

    @Test
    void testConsume_InvalidUUID() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", "invalid-uuid");

        retryRCAConsumer.consume(record);

        verify(rcaService, never()).retryRCA(any(UUID.class));
    }

    @Test
    void testConsume_Exception() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", id.toString());

        doThrow(new RuntimeException("Test Exception")).when(rcaService).retryRCA(id);

        retryRCAConsumer.consume(record);

        verify(rcaService).retryRCA(id);
    }
}
