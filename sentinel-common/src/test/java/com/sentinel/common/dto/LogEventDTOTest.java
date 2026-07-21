package com.sentinel.common.dto;

import com.sentinel.common.enums.LogLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogEventDTOTest {

    @Test
    void testBuilderAndGetters() {
        LocalDateTime now = LocalDateTime.now();
        LogEventDTO dto = LogEventDTO.builder()
                .eventId("123")
                .timestamp(now)
                .serviceName("service")
                .logLevel(LogLevel.INFO)
                .message("msg")
                .stackTrace("stack")
                .requestId("req")
                .latencyMs(100)
                .statusCode(200)
                .host("host")
                .metadata(Map.of("key", "val"))
                .build();

        assertEquals("123", dto.getEventId());
        assertEquals(now, dto.getTimestamp());
        assertEquals("service", dto.getServiceName());
        assertEquals(LogLevel.INFO, dto.getLogLevel());
        assertEquals("msg", dto.getMessage());
        assertEquals("stack", dto.getStackTrace());
        assertEquals("req", dto.getRequestId());
        assertEquals(100, dto.getLatencyMs());
        assertEquals(200, dto.getStatusCode());
        assertEquals("host", dto.getHost());
        assertNotNull(dto.getMetadata());
        assertEquals("val", dto.getMetadata().get("key"));

        // Test setters and NoArgsConstructor
        LogEventDTO empty = new LogEventDTO();
        empty.setEventId("456");
        assertEquals("456", empty.getEventId());
    }
}
