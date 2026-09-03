package com.sentinel.rca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.common.enums.AnomalyType;
import com.sentinel.rca.entity.AnomalyRecord;
import com.sentinel.rca.entity.Incident;
import com.sentinel.rca.entity.LogEvent;
import com.sentinel.rca.llm.OllamaClient;
import com.sentinel.rca.llm.OpenRouterClient;
import com.sentinel.rca.model.RCAResponse;
import com.sentinel.rca.repository.AnomalyRecordRepository;
import com.sentinel.rca.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RCAServiceTest {

    @Mock
    private ContextGatherer contextGatherer;
    @Mock
    private PromptBuilder promptBuilder;
    @Mock
    private OpenRouterClient openRouterClient;
    @Mock
    private OllamaClient ollamaClient;
    @Mock
    private LLMCacheService cacheService;
    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private AnomalyRecordRepository anomalyRecordRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RCAService rcaService;

    @Test
    void testPerformRCA_IncidentAlreadyExists() {
        AnomalyDTO anomaly = new AnomalyDTO();
        anomaly.setAnomalyId(UUID.randomUUID().toString());

        when(incidentRepository.existsByAnomalyId(any(UUID.class))).thenReturn(true);

        rcaService.performRCA(anomaly);

        verify(anomalyRecordRepository, never()).updateStatus(any(UUID.class), anyString());
    }

    @Test
    void testPerformRCA_SuccessWithOpenRouter() throws Exception {
        UUID anomalyId = UUID.randomUUID();
        AnomalyDTO anomaly = AnomalyDTO.builder()
                .anomalyId(anomalyId.toString())
                .serviceName("auth-service")
                .anomalyType(AnomalyType.ERROR_SPIKE)
                .metricName("error_rate")
                .build();

        Incident incident = new Incident();
        incident.setIncidentId(UUID.randomUUID());

        when(incidentRepository.existsByAnomalyId(anomalyId)).thenReturn(false);
        when(contextGatherer.gatherRawLogs(anomaly)).thenReturn(List.of(new LogEvent()));
        when(contextGatherer.formatLogsForPrompt(any())).thenReturn("Logs");
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(cacheService.buildCacheKey(any(), any(), any())).thenReturn("cacheKey");
        when(cacheService.get("cacheKey")).thenReturn(Optional.empty());
        
        when(promptBuilder.build(anomaly, "Logs")).thenReturn("Prompt");
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(openRouterClient.isAvailable()).thenReturn(true);
        
        RCAResponse response = RCAResponse.builder()
                .parseSuccess(true)
                .confidence(0.9)
                .rootCause("DB_TIMEOUT")
                .title("DB Down")
                .build();
        when(openRouterClient.generateRCA("Prompt")).thenReturn(response);

        rcaService.performRCA(anomaly);

        verify(anomalyRecordRepository).updateStatus(anomalyId, "INVESTIGATING");
        verify(incidentRepository, times(3)).save(any(Incident.class)); // Create, ASSESSING, RCA_COMPLETE
        verify(anomalyRecordRepository).updateStatus(anomalyId, "INVESTIGATED");
        verify(cacheService).put(eq("cacheKey"), any());
    }

    @Test
    void testPerformRCA_CachedResponse() throws Exception {
        UUID anomalyId = UUID.randomUUID();
        AnomalyDTO anomaly = AnomalyDTO.builder()
                .anomalyId(anomalyId.toString())
                .serviceName("auth-service")
                .anomalyType(AnomalyType.ERROR_SPIKE)
                .metricName("error_rate")
                .build();

        Incident incident = new Incident();
        incident.setIncidentId(UUID.randomUUID());

        when(incidentRepository.existsByAnomalyId(anomalyId)).thenReturn(false);
        when(contextGatherer.gatherRawLogs(anomaly)).thenReturn(Collections.emptyList());
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(cacheService.buildCacheKey(any(), any(), any())).thenReturn("cacheKey");
        
        when(cacheService.get("cacheKey")).thenReturn(Optional.of("{}"));
        RCAResponse response = RCAResponse.builder()
                .parseSuccess(true)
                .confidence(0.9)
                .rootCause("DB_TIMEOUT")
                .build();
        when(objectMapper.readValue("{}", RCAResponse.class)).thenReturn(response);

        rcaService.performRCA(anomaly);

        verify(openRouterClient, never()).generateRCA(anyString());
        verify(anomalyRecordRepository).updateStatus(anomalyId, "INVESTIGATED");
    }

    @Test
    void testPerformRCA_RateLimitFallback() throws Exception {
        UUID anomalyId = UUID.randomUUID();
        AnomalyDTO anomaly = AnomalyDTO.builder()
                .anomalyId(anomalyId.toString())
                .serviceName("auth-service")
                .anomalyType(AnomalyType.ERROR_SPIKE)
                .metricName("error_rate")
                .build();

        Incident incident = new Incident();
        incident.setIncidentId(UUID.randomUUID());

        when(incidentRepository.existsByAnomalyId(anomalyId)).thenReturn(false);
        when(contextGatherer.gatherRawLogs(anomaly)).thenReturn(List.of(new LogEvent()));
        when(contextGatherer.formatLogsForPrompt(any())).thenReturn("Logs");
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(cacheService.buildCacheKey(any(), any(), any())).thenReturn("cacheKey");
        when(cacheService.get("cacheKey")).thenReturn(Optional.empty());
        
        when(promptBuilder.build(any(), any())).thenReturn("Prompt");
        when(rateLimiter.tryAcquire()).thenReturn(false);
        when(ollamaClient.isAvailable()).thenReturn(true);
        
        RCAResponse response = RCAResponse.builder()
                .parseSuccess(true)
                .confidence(0.8)
                .build();
        when(ollamaClient.generateRCA("Prompt")).thenReturn(response);

        rcaService.performRCA(anomaly);

        verify(ollamaClient).generateRCA("Prompt");
    }
}
