package com.sentinel.simulator.generator;

import com.sentinel.common.dto.LogEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NormalTrafficGeneratorTest {

    private NormalTrafficGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new NormalTrafficGenerator();
    }

    @Test
    void testGenerateNormalEvent() {
        // Run it enough times to hit all branches (INFO, WARN, ERROR, DEBUG, and all services)
        boolean errorHit = false;
        boolean warnHit = false;
        boolean debugHit = false;
        boolean infoHit = false;

        for (int i = 0; i < 2000; i++) {
            LogEventDTO event = generator.generateNormalEvent();

            assertNotNull(event);
            assertNotNull(event.getEventId());
            assertNotNull(event.getTimestamp());
            assertNotNull(event.getServiceName());
            assertNotNull(event.getLogLevel());
            assertNotNull(event.getMessage());
            assertNotNull(event.getRequestId());
            assertNotNull(event.getHost());
            assertNotNull(event.getMetadata());

            switch (event.getLogLevel()) {
                case INFO:
                    infoHit = true;
                    assertTrue(event.getStatusCode() == 200);
                    assertNull(event.getStackTrace());
                    break;
                case WARN:
                    warnHit = true;
                    assertTrue(event.getStatusCode() == 200 || event.getStatusCode() == 429);
                    assertNull(event.getStackTrace());
                    break;
                case ERROR:
                    errorHit = true;
                    assertTrue(event.getStatusCode() >= 500);
                    assertNotNull(event.getStackTrace());
                    break;
                case DEBUG:
                    debugHit = true;
                    assertTrue(event.getStatusCode() == 200);
                    assertNull(event.getStackTrace());
                    break;
            }

            assertTrue(event.getLatencyMs() >= 0);
            assertTrue(event.getHost().contains("-pod-"));
            assertEquals("prod", event.getMetadata().get("env"));
        }

        assertTrue(infoHit, "INFO level was never generated");
        assertTrue(warnHit, "WARN level was never generated");
        assertTrue(errorHit, "ERROR level was never generated");
        assertTrue(debugHit, "DEBUG level was never generated");
    }

    @Test
    void testGetServices() {
        List<String> services = generator.getServices();
        assertNotNull(services);
        assertEquals(5, services.size());
        assertTrue(services.contains("payment-service"));
    }
}
