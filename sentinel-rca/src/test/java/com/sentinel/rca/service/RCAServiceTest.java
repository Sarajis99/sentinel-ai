package com.sentinel.rca.service;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.common.enums.AnomalyType;
import com.sentinel.common.enums.Severity;
import com.sentinel.rca.entity.Incident;
import com.sentinel.rca.llm.OllamaClient;
import com.sentinel.rca.llm.OpenRouterClient;
import com.sentinel.rca.model.RCAResponse;
import com.sentinel.rca.repository.AnomalyRecordRepository;
import com.sentinel.rca.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RCAService Tests")
class RCAServiceTest {

    @Mock private ContextGatherer contextGatherer;
    @Mock private PromptBuilder promptBuilder;
    @Mock private OpenRouterClient openRouterClient;
    @Mock private OllamaClient ollamaClient;
    @Mock private LLMCacheService cacheService;
    @Mock private RateLimiter rateLimiter;
    @Mock private IncidentRepository incidentRepository;
    @Mock private AnomalyRecordRepository anomalyRecordRepository;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private RCAService rcaService;

    private AnomalyDTO testAnomaly;
    private RCAResponse testRcaResponse;

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
                .detectedAt(LocalDateTime.now())
                .windowMinutes(5)
                .build();

        testRcaResponse = RCAResponse.builder()
                .rootCause("DB_OUTAGE")
                .title("Payment Service DB Connection Pool Exhausted")
                .rcaSummary("Database connection pool was exhausted due to HikariCP misconfiguration.")
                .rootCauseDetail("Logs show repeated 'Unable to acquire JDBC Connection' errors.")
                .impactAnalysis("All payment processing is affected. 100% failure rate.")
                .suggestedFix("1. Increase max-pool-size to 20. 2. Restart payment-service.")
                .prevention("Set up pool monitoring alerts. Use circuit breaker pattern.")
                .confidence(0.92)
                .parseSuccess(true)
                .build();
    }

    @Test
    @DisplayName("Should complete full RCA pipeline with OpenRouter")
    void shouldPerformFullRCAWithOpenRouter() throws Exception {
        // Arrange
        UUID anomalyId = UUID.fromString(testAnomaly.getAnomalyId());
        when(incidentRepository.existsByAnomalyId(anomalyId)).thenReturn(false);
        when(cacheService.buildCacheKey(any(), any(), any())).thenReturn("rca:cache:payment-service:ERROR_SPIKE:error_rate");
        when(cacheService.get(any())).thenReturn(Optional.empty());
        when(contextGatherer.gatherContext(testAnomaly)).thenReturn("Sample log context");
        when(promptBuilder.build(any(), any())).thenReturn("Sample prompt");
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(openRouterClient.isAvailable()).thenReturn(true);
        when(openRouterClient.generateRCA(any())).thenReturn(testRcaResponse);
        when(incidentRepository.save(any())).thenReturn(new Incident());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        rcaService.performRCA(testAnomaly);

        // Assert
        verify(anomalyRecordRepository).updateStatus(anomalyId, "INVESTIGATING");
        verify(contextGatherer).gatherContext(testAnomaly);
        verify(promptBuilder).build(eq(testAnomaly), any());
        verify(openRouterClient).generateRCA(any());
        verify(incidentRepository).save(any(Incident.class));
        verify(anomalyRecordRepository).updateStatus(anomalyId, "INVESTIGATED");
    }

    @Test
    @DisplayName("Should skip if incident already exists (idempotency)")
    void shouldSkipIfIncidentAlreadyExists() {
        // Arrange
        UUID anomalyId = UUID.fromString(testAnomaly.getAnomalyId());
        when(incidentRepository.existsByAnomalyId(anomalyId)).thenReturn(true);

        // Act
        rcaService.performRCA(testAnomaly);

        // Assert
        verify(contextGatherer, never()).gatherContext(any());
        verify(openRouterClient, never()).generateRCA(any());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fall back to Ollama when OpenRouter is unavailable")
    void shouldFallbackToOllamaWhenOpenRouterUnavailable() throws Exception {
        // Arrange
        UUID anomalyId = UUID.fromString(testAnomaly.getAnomalyId());
        when(incidentRepository.existsByAnomalyId(anomalyId)).thenReturn(false);
        when(cacheService.buildCacheKey(any(), any(), any())).thenReturn("cache-key");
        when(cacheService.get(any())).thenReturn(Optional.empty());
        when(contextGatherer.gatherContext(any())).thenReturn("log context");
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(openRouterClient.isAvailable()).thenReturn(false);
        when(ollamaClient.isAvailable()).thenReturn(true);
        when(ollamaClient.generateRCA(any())).thenReturn(testRcaResponse);
        when(incidentRepository.save(any())).thenReturn(new Incident());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        rcaService.performRCA(testAnomaly);

        // Assert
        verify(openRouterClient, never()).generateRCA(any());
        verify(ollamaClient).generateRCA(any());
        verify(incidentRepository).save(any());
    }

    @Test
    @DisplayName("Should use cached RCA when available")
    void shouldUseCachedRCAWhenAvailable() throws Exception {
        // Arrange
        UUID anomalyId = UUID.fromString(testAnomaly.getAnomalyId());
        String cachedJson = "{\"rootCause\":\"DB_OUTAGE\",\"confidence\":0.9}";
        when(incidentRepository.existsByAnomalyId(anomalyId)).thenReturn(false);
        when(cacheService.buildCacheKey(any(), any(), any())).thenReturn("cache-key");
        when(cacheService.get("cache-key")).thenReturn(Optional.of(cachedJson));
        when(objectMapper.readValue(cachedJson, RCAResponse.class)).thenReturn(testRcaResponse);
        when(incidentRepository.save(any())).thenReturn(new Incident());

        // Act
        rcaService.performRCA(testAnomaly);

        // Assert — LLM should NOT be called when cache hits
        verify(contextGatherer, never()).gatherContext(any());
        verify(openRouterClient, never()).generateRCA(any());
        verify(ollamaClient, never()).generateRCA(any());
        verify(incidentRepository).save(any());
    }

    @Test
    @DisplayName("Should revert anomaly status to DETECTED on failure")
    void shouldRevertStatusOnFailure() {
        // Arrange
        UUID anomalyId = UUID.fromString(testAnomaly.getAnomalyId());
        when(incidentRepository.existsByAnomalyId(anomalyId)).thenReturn(false);
        when(cacheService.buildCacheKey(any(), any(), any())).thenReturn("cache-key");
        when(cacheService.get(any())).thenReturn(Optional.empty());
        when(contextGatherer.gatherContext(any())).thenThrow(new RuntimeException("DB connection failed"));

        // Act
        rcaService.performRCA(testAnomaly);

        // Assert
        verify(anomalyRecordRepository).updateStatus(anomalyId, "INVESTIGATING");
        verify(anomalyRecordRepository).updateStatus(anomalyId, "DETECTED"); // Reverted
        verify(incidentRepository, never()).save(any());
    }
}
