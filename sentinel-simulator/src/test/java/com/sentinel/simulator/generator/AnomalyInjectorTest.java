package com.sentinel.simulator.generator;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyInjectorTest {

    private AnomalyInjector injector;

    @BeforeEach
    void setUp() {
        injector = new AnomalyInjector();
    }

    @Test
    void testInjectAnomalyErrorSpike() {
        String service = "payment-service";
        List<LogEventDTO> events = injector.injectAnomaly(service, AnomalyInjector.AnomalyScenario.ERROR_SPIKE);

        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertTrue(events.size() >= 40 && events.size() < 50);

        for (LogEventDTO event : events) {
            assertEquals(service, event.getServiceName());
            assertEquals(LogLevel.ERROR, event.getLogLevel());
            assertTrue(event.getMessage().contains("Unhandled exception"));
            assertNotNull(event.getStackTrace());
            assertEquals(500, event.getStatusCode());
            assertEquals("error_spike", event.getMetadata().get("anomaly"));
            assertEquals("prod", event.getMetadata().get("env"));
        }
    }

    @Test
    void testInjectAnomalyLatencySurge() {
        String service = "order-service";
        List<LogEventDTO> events = injector.injectAnomaly(service, AnomalyInjector.AnomalyScenario.LATENCY_SURGE);

        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertTrue(events.size() >= 30 && events.size() < 40);

        for (LogEventDTO event : events) {
            assertEquals(service, event.getServiceName());
            assertTrue(event.getLatencyMs() >= 3000 && event.getLatencyMs() < 8000);
            assertEquals("latency_surge", event.getMetadata().get("anomaly"));
        }
    }

    @Test
    void testInjectAnomalyDbOutage() {
        String service = "inventory-service";
        List<LogEventDTO> events = injector.injectAnomaly(service, AnomalyInjector.AnomalyScenario.DB_OUTAGE);

        assertNotNull(events);
        assertEquals(35, events.size());

        for (LogEventDTO event : events) {
            assertEquals(service, event.getServiceName());
            assertEquals(LogLevel.ERROR, event.getLogLevel());
            assertTrue(event.getMessage().contains("Cannot acquire database connection"));
            assertNotNull(event.getStackTrace());
            assertEquals(30000, event.getLatencyMs());
            assertEquals(503, event.getStatusCode());
            assertEquals("db_outage", event.getMetadata().get("anomaly"));
        }
    }

    @Test
    void testInjectAnomalyMemoryLeak() {
        String service = "user-service";
        List<LogEventDTO> events = injector.injectAnomaly(service, AnomalyInjector.AnomalyScenario.MEMORY_LEAK);

        assertNotNull(events);
        assertEquals(25, events.size());

        // Last event should be OOM
        LogEventDTO lastEvent = events.get(24);
        assertEquals(LogLevel.ERROR, lastEvent.getLogLevel());
        assertTrue(lastEvent.getMessage().contains("OutOfMemoryError"));
        assertNotNull(lastEvent.getStackTrace());
        assertEquals(500, lastEvent.getStatusCode());
        assertEquals(service + "-pod-0", lastEvent.getHost());

        for (LogEventDTO event : events) {
            assertEquals("memory_leak", event.getMetadata().get("anomaly"));
        }
    }

    @Test
    void testInjectAnomalyDownstreamFailure() {
        String service = "notification-service";
        List<LogEventDTO> events = injector.injectAnomaly(service, AnomalyInjector.AnomalyScenario.DOWNSTREAM_FAILURE);

        assertNotNull(events);
        assertEquals(30, events.size());

        for (LogEventDTO event : events) {
            assertEquals("downstream_failure", event.getMetadata().get("anomaly"));
            assertTrue(event.getLatencyMs() >= 5000 && event.getLatencyMs() < 8000);
        }
    }

    @Test
    void testPickRandomScenario() {
        AnomalyInjector.AnomalyScenario scenario = injector.pickRandomScenario();
        assertNotNull(scenario);
    }
}
