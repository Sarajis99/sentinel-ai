package com.sentinel.simulator.scheduler;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.simulator.generator.AnomalyInjector;
import com.sentinel.simulator.generator.NormalTrafficGenerator;
import com.sentinel.simulator.producer.LogEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

class SimulationSchedulerTest {

    private NormalTrafficGenerator trafficGenerator;
    private AnomalyInjector anomalyInjector;
    private LogEventProducer logProducer;
    private SimulationScheduler scheduler;

    @BeforeEach
    void setUp() {
        trafficGenerator = mock(NormalTrafficGenerator.class);
        anomalyInjector = mock(AnomalyInjector.class);
        logProducer = mock(LogEventProducer.class);
        scheduler = new SimulationScheduler(trafficGenerator, anomalyInjector, logProducer);
    }

    @Test
    void testSendNormalTraffic() {
        LogEventDTO mockEvent = LogEventDTO.builder().build();
        when(trafficGenerator.generateNormalEvent()).thenReturn(mockEvent);

        for (int i = 0; i < 100; i++) {
            scheduler.sendNormalTraffic();
        }

        verify(trafficGenerator, times(100)).generateNormalEvent();
        verify(logProducer, times(100)).send(mockEvent);
    }

    @Test
    void testInjectAnomaly() {
        List<LogEventDTO> mockEvents = new ArrayList<>();
        LogEventDTO event1 = LogEventDTO.builder().build();
        LogEventDTO event2 = LogEventDTO.builder().build();
        mockEvents.add(event1);
        mockEvents.add(event2);

        List<String> mockServices = List.of("payment-service");
        when(trafficGenerator.getServices()).thenReturn(mockServices);

        when(anomalyInjector.pickRandomScenario()).thenReturn(AnomalyInjector.AnomalyScenario.ERROR_SPIKE);
        when(anomalyInjector.injectAnomaly(eq("payment-service"), eq(AnomalyInjector.AnomalyScenario.ERROR_SPIKE)))
                .thenReturn(mockEvents);

        scheduler.injectAnomaly();

        verify(trafficGenerator, times(1)).getServices();
        verify(anomalyInjector, times(1)).pickRandomScenario();
        verify(anomalyInjector, times(1)).injectAnomaly(eq("payment-service"), eq(AnomalyInjector.AnomalyScenario.ERROR_SPIKE));
        verify(logProducer, times(1)).send(event1);
        verify(logProducer, times(1)).send(event2);
    }
}
