package com.sentinel.rca.service;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.rca.entity.LogEvent;
import com.sentinel.rca.repository.LogEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextGathererTest {

    @Mock
    private LogEventRepository logEventRepository;

    @InjectMocks
    private ContextGatherer contextGatherer;

    @Test
    void testGatherRawLogsWithCriticalLogs() {
        ReflectionTestUtils.setField(contextGatherer, "windowMinutes", 5);
        ReflectionTestUtils.setField(contextGatherer, "maxLogEntries", 50);

        AnomalyDTO anomaly = AnomalyDTO.builder()
                .serviceName("auth-service")
                .detectedAt(LocalDateTime.now())
                .build();

        List<LogEvent> criticalLogs = Arrays.asList(
                new LogEvent(), new LogEvent(), new LogEvent(), new LogEvent(), new LogEvent()
        );

        when(logEventRepository.findCriticalLogsByServiceAndWindow(eq("auth-service"), any(), any()))
                .thenReturn(criticalLogs);

        List<LogEvent> result = contextGatherer.gatherRawLogs(anomaly);
        
        assertEquals(5, result.size());
        verify(logEventRepository, never()).findByServiceNameAndTimestampBetween(any(), any(), any());
    }

    @Test
    void testGatherRawLogsWithFewCriticalLogs() {
        ReflectionTestUtils.setField(contextGatherer, "windowMinutes", 5);
        ReflectionTestUtils.setField(contextGatherer, "maxLogEntries", 50);

        AnomalyDTO anomaly = AnomalyDTO.builder()
                .serviceName("auth-service")
                .detectedAt(LocalDateTime.now())
                .build();

        List<LogEvent> criticalLogs = Collections.singletonList(new LogEvent());
        List<LogEvent> allLogs = Arrays.asList(new LogEvent(), new LogEvent());

        when(logEventRepository.findCriticalLogsByServiceAndWindow(eq("auth-service"), any(), any()))
                .thenReturn(criticalLogs);
        when(logEventRepository.findByServiceNameAndTimestampBetween(eq("auth-service"), any(), any()))
                .thenReturn(allLogs);

        List<LogEvent> result = contextGatherer.gatherRawLogs(anomaly);
        
        assertEquals(2, result.size());
    }

    @Test
    void testFormatLogsForPrompt() {
        LogEvent event = new LogEvent();
        event.setTimestamp(LocalDateTime.of(2023, 1, 1, 12, 0));
        event.setLogLevel("ERROR");
        event.setServiceName("auth-service");
        event.setMessage("DB Timeout");
        event.setLatencyMs(150);
        event.setStatusCode(500);
        event.setStackTrace("at com.example.MyClass.method(MyClass.java:10)\n at com.example.Other.method(Other.java:20)");

        String formatted = contextGatherer.formatLogsForPrompt(Collections.singletonList(event));
        
        assertTrue(formatted.contains("RAW LOG ENTRIES (1 entries)"));
        assertTrue(formatted.contains("[2023-01-01T12:00]"));
        assertTrue(formatted.contains("[ERROR]"));
        assertTrue(formatted.contains("auth-service"));
        assertTrue(formatted.contains("DB Timeout"));
        assertTrue(formatted.contains("latency=150ms"));
        assertTrue(formatted.contains("status=500"));
        assertTrue(formatted.contains("at com.example.MyClass"));
    }
}
