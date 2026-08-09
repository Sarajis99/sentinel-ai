package com.sentinel.api.service;

import com.sentinel.common.dto.LogEventDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChaosServiceTest {

    @Mock
    private KafkaTemplate<String, LogEventDTO> kafkaTemplate;

    @Mock
    private SimulationService simulationService;

    @InjectMocks
    private ChaosService chaosService;

    @Test
    void getAvailableServices() {
        List<String> services = chaosService.getAvailableServices();
        assertFalse(services.isEmpty());
        assertTrue(services.contains("payment-service"));
    }

    @Test
    void getAvailableScenarios() {
        var scenarios = chaosService.getAvailableScenarios();
        assertFalse(scenarios.isEmpty());
    }

    @Test
    void injectAnomaly_SimulationNotActive() {
        when(simulationService.isSimulationActive()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> chaosService.injectAnomaly("ERROR_SPIKE", "payment-service"));
    }

    @Test
    void injectAnomaly_ErrorSpike() {
        when(simulationService.isSimulationActive()).thenReturn(true);

        List<LogEventDTO> events = chaosService.injectAnomaly("ERROR_SPIKE", "payment-service");

        assertFalse(events.isEmpty());
        verify(kafkaTemplate, times(events.size())).send(eq("log-events"), eq("payment-service"), any(LogEventDTO.class));
    }

    @Test
    void injectAnomaly_LatencySurge() {
        when(simulationService.isSimulationActive()).thenReturn(true);
        List<LogEventDTO> events = chaosService.injectAnomaly("LATENCY_SURGE", "payment-service");
        assertFalse(events.isEmpty());
    }

    @Test
    void injectAnomaly_DbOutage() {
        when(simulationService.isSimulationActive()).thenReturn(true);
        List<LogEventDTO> events = chaosService.injectAnomaly("DB_OUTAGE", "payment-service");
        assertFalse(events.isEmpty());
    }

    @Test
    void injectAnomaly_MemoryLeak() {
        when(simulationService.isSimulationActive()).thenReturn(true);
        List<LogEventDTO> events = chaosService.injectAnomaly("MEMORY_LEAK", "payment-service");
        assertFalse(events.isEmpty());
    }

    @Test
    void injectAnomaly_DownstreamFailure() {
        when(simulationService.isSimulationActive()).thenReturn(true);
        List<LogEventDTO> events = chaosService.injectAnomaly("DOWNSTREAM_FAILURE", "payment-service");
        assertFalse(events.isEmpty());
    }

    @Test
    void injectAnomaly_RateLimitSpike() {
        when(simulationService.isSimulationActive()).thenReturn(true);
        List<LogEventDTO> events = chaosService.injectAnomaly("RATE_LIMIT_SPIKE", "payment-service");
        assertFalse(events.isEmpty());
    }

    @Test
    void injectAnomaly_ConfigError() {
        when(simulationService.isSimulationActive()).thenReturn(true);
        List<LogEventDTO> events = chaosService.injectAnomaly("CONFIG_ERROR", "payment-service");
        assertFalse(events.isEmpty());
    }

    @Test
    void injectAnomaly_InvalidScenario() {
        when(simulationService.isSimulationActive()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> chaosService.injectAnomaly("INVALID", "payment-service"));
    }
}
