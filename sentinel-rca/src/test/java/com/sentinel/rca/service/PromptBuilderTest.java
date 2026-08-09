package com.sentinel.rca.service;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.common.enums.AnomalyType;
import com.sentinel.common.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void testBuildPrompt() {
        AnomalyDTO anomaly = AnomalyDTO.builder()
                .anomalyId("1234")
                .serviceName("auth-service")
                .detectedAt(LocalDateTime.of(2023, 1, 1, 12, 0))
                .anomalyType(AnomalyType.ERROR_SPIKE)
                .severity(Severity.P0)
                .metricName("error_rate")
                .expectedValue(5.0)
                .actualValue(15.0)
                .windowMinutes(5)
                .build();

        String logContext = "=== RAW LOG ENTRIES (1 entries) ===\n[2023-01-01T12:00:00] [ERROR] auth-service - DB Timeout";

        String prompt = promptBuilder.build(anomaly, logContext);

        assertNotNull(prompt);
        assertTrue(prompt.contains("auth-service"));
        assertTrue(prompt.contains("ERROR_SPIKE"));
        assertTrue(prompt.contains("P0"));
        assertTrue(prompt.contains("error_rate"));
        assertTrue(prompt.contains("5.0000"));
        assertTrue(prompt.contains("15.0000"));
        assertTrue(prompt.contains("200.0%")); // Deviation
        assertTrue(prompt.contains("DB Timeout"));
    }
}
