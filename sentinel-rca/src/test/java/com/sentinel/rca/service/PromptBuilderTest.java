package com.sentinel.rca.service;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.common.enums.AnomalyType;
import com.sentinel.common.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromptBuilder Tests")
class PromptBuilderTest {

    @InjectMocks
    private PromptBuilder promptBuilder;

    private AnomalyDTO testAnomaly;

    @BeforeEach
    void setUp() {
        testAnomaly = AnomalyDTO.builder()
                .anomalyId(UUID.randomUUID().toString())
                .serviceName("payment-service")
                .anomalyType(AnomalyType.ERROR_SPIKE)
                .severity(Severity.P0)
                .metricName("error_rate")
                .expectedValue(0.05)
                .actualValue(0.35)
                .detectedAt(LocalDateTime.of(2026, 7, 26, 12, 0, 0))
                .windowMinutes(5)
                .build();
    }

    @Test
    @DisplayName("Should include service name in prompt")
    void shouldIncludeServiceNameInPrompt() {
        String prompt = promptBuilder.build(testAnomaly, "Log context here");
        assertThat(prompt).contains("payment-service");
    }

    @Test
    @DisplayName("Should include anomaly type in prompt")
    void shouldIncludeAnomalyTypeInPrompt() {
        String prompt = promptBuilder.build(testAnomaly, "Log context here");
        assertThat(prompt).contains("ERROR_SPIKE");
    }

    @Test
    @DisplayName("Should include severity in prompt")
    void shouldIncludeSeverityInPrompt() {
        String prompt = promptBuilder.build(testAnomaly, "Log context here");
        assertThat(prompt).contains("P0");
    }

    @Test
    @DisplayName("Should include metric name in prompt")
    void shouldIncludeMetricNameInPrompt() {
        String prompt = promptBuilder.build(testAnomaly, "Log context here");
        assertThat(prompt).contains("error_rate");
    }

    @Test
    @DisplayName("Should include expected and actual values in prompt")
    void shouldIncludeValuesInPrompt() {
        String prompt = promptBuilder.build(testAnomaly, "Log context here");
        assertThat(prompt).contains("0.0500");
        assertThat(prompt).contains("0.3500");
    }

    @Test
    @DisplayName("Should include log context in prompt")
    void shouldIncludeLogContextInPrompt() {
        String logContext = "FATAL: Cannot acquire database connection";
        String prompt = promptBuilder.build(testAnomaly, logContext);
        assertThat(prompt).contains(logContext);
    }

    @Test
    @DisplayName("Should calculate correct deviation percentage")
    void shouldCalculateDeviationPercentage() {
        // (0.35 - 0.05) / 0.05 * 100 = 600%
        String prompt = promptBuilder.build(testAnomaly, "context");
        assertThat(prompt).contains("600.0%");
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void shouldHandleNullValuesGracefully() {
        AnomalyDTO anomalyWithNulls = AnomalyDTO.builder()
                .anomalyId(UUID.randomUUID().toString())
                .serviceName("test-service")
                .anomalyType(AnomalyType.ERROR_SPIKE)
                .severity(Severity.P2)
                .metricName("error_rate")
                .expectedValue(null)
                .actualValue(null)
                .detectedAt(LocalDateTime.now())
                .windowMinutes(null)
                .build();

        // Should not throw any exception
        String prompt = promptBuilder.build(anomalyWithNulls, "context");
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("test-service");
    }
}
