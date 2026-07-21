package com.sentinel.common.dto;

import com.sentinel.common.enums.AnomalyType;
import com.sentinel.common.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnomalyDTOTest {

    @Test
    void testBuilderAndGetters() {
        LocalDateTime now = LocalDateTime.now();
        AnomalyDTO dto = AnomalyDTO.builder()
                .anomalyId("123")
                .detectedAt(now)
                .serviceName("service")
                .anomalyType(AnomalyType.ERROR_SPIKE)
                .severity(Severity.P1)
                .metricName("error_rate")
                .expectedValue(0.0)
                .actualValue(10.0)
                .zScore(0.95)
                .windowMinutes(5)
                .build();

        assertEquals("123", dto.getAnomalyId());
        assertEquals(now, dto.getDetectedAt());
        assertEquals("service", dto.getServiceName());
        assertEquals(AnomalyType.ERROR_SPIKE, dto.getAnomalyType());
        assertEquals(Severity.P1, dto.getSeverity());
        assertEquals("error_rate", dto.getMetricName());
        assertEquals(0.0, dto.getExpectedValue());
        assertEquals(10.0, dto.getActualValue());
        assertEquals(0.95, dto.getZScore());
        assertEquals(5, dto.getWindowMinutes());

        AnomalyDTO empty = new AnomalyDTO();
        empty.setAnomalyId("456");
        assertEquals("456", empty.getAnomalyId());
    }
}
