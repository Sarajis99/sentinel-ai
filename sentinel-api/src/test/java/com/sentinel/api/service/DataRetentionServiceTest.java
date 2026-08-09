package com.sentinel.api.service;

import com.sentinel.api.repository.AnomalyRecordRepository;
import com.sentinel.api.repository.IncidentRepository;
import com.sentinel.api.repository.LogEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock
    private LogEventRepository logEventRepository;
    @Mock
    private AnomalyRecordRepository anomalyRecordRepository;
    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private DataRetentionService dataRetentionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataRetentionService, "logEventsMaxAgeHours", 24);
        ReflectionTestUtils.setField(dataRetentionService, "anomaliesMaxAgeHours", 24);
        ReflectionTestUtils.setField(dataRetentionService, "incidentsMaxCount", 1000);
    }

    @Test
    void performRetentionCleanup() {
        when(logEventRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(100);
        when(anomalyRecordRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(50);
        when(incidentRepository.findIdsOlderThanOffset(1000)).thenReturn(List.of(1L, 2L));
        when(incidentRepository.deleteByIds(anyList())).thenReturn(2);

        dataRetentionService.performRetentionCleanup();

        verify(logEventRepository).deleteOlderThan(any(LocalDateTime.class));
        verify(anomalyRecordRepository).deleteOlderThan(any(LocalDateTime.class));
        verify(incidentRepository).findIdsOlderThanOffset(1000);
        verify(incidentRepository).deleteByIds(List.of(1L, 2L));
    }

    @Test
    void triggerManualCleanup() {
        when(logEventRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(0);
        when(anomalyRecordRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(0);
        when(incidentRepository.findIdsOlderThanOffset(1000)).thenReturn(List.of());

        dataRetentionService.triggerManualCleanup();

        verify(logEventRepository).deleteOlderThan(any(LocalDateTime.class));
        verify(anomalyRecordRepository).deleteOlderThan(any(LocalDateTime.class));
        verify(incidentRepository).findIdsOlderThanOffset(1000);
        verify(incidentRepository, never()).deleteByIds(anyList());
    }
}
