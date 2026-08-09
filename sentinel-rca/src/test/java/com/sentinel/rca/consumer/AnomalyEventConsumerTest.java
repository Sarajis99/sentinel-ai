package com.sentinel.rca.consumer;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.rca.service.RCAService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyEventConsumerTest {

    @Mock
    private RCAService rcaService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private AnomalyEventConsumer anomalyEventConsumer;

    @Test
    void testConsume_Success() {
        AnomalyDTO anomaly = new AnomalyDTO();
        anomaly.setAnomalyId("123");
        ConsumerRecord<String, AnomalyDTO> record = new ConsumerRecord<>("topic", 0, 0, "key", anomaly);

        doNothing().when(rcaService).performRCA(anomaly);

        anomalyEventConsumer.consume(record, acknowledgment);

        verify(rcaService).performRCA(anomaly);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testConsume_Exception() {
        AnomalyDTO anomaly = new AnomalyDTO();
        anomaly.setAnomalyId("123");
        ConsumerRecord<String, AnomalyDTO> record = new ConsumerRecord<>("topic", 0, 0, "key", anomaly);

        doThrow(new RuntimeException("Test Exception")).when(rcaService).performRCA(anomaly);

        anomalyEventConsumer.consume(record, acknowledgment);

        verify(rcaService).performRCA(anomaly);
        verify(acknowledgment, never()).acknowledge();
    }
}
