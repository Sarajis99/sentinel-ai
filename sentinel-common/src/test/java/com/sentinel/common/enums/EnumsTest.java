package com.sentinel.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EnumsTest {

    @Test
    void testLogLevelEnum() {
        LogLevel[] values = LogLevel.values();
        assertNotNull(values);
        assertEquals(4, values.length);
        assertEquals(LogLevel.INFO, LogLevel.valueOf("INFO"));
    }

    @Test
    void testAnomalyTypeEnum() {
        AnomalyType[] values = AnomalyType.values();
        assertNotNull(values);
        assertEquals(4, values.length);
        assertEquals(AnomalyType.ERROR_SPIKE, AnomalyType.valueOf("ERROR_SPIKE"));
    }

    @Test
    void testSeverityEnum() {
        Severity[] values = Severity.values();
        assertNotNull(values);
        assertEquals(4, values.length);
        assertEquals(Severity.P1, Severity.valueOf("P1"));
    }
}
