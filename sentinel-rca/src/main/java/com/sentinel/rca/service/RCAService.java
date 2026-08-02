package com.sentinel.rca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.rca.entity.Incident;
import com.sentinel.rca.entity.LogEvent;
import com.sentinel.rca.llm.LLMClient;
import com.sentinel.rca.llm.OllamaClient;
import com.sentinel.rca.llm.OpenRouterClient;
import com.sentinel.rca.model.RCAResponse;
import com.sentinel.rca.repository.AnomalyRecordRepository;
import com.sentinel.rca.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RCAService — the main orchestrator for Phase 3.
 *
 * Full pipeline:
 *   1. Mark anomaly as INVESTIGATING
 *   2. Check Redis cache for existing RCA
 *   3. Gather raw log context from PostgreSQL
 *   4. Build LLM prompt
 *   5. Call OpenRouter (with Ollama fallback) to generate RCA
 *   6. Parse and save incident to PostgreSQL
 *   7. Update anomaly status to INVESTIGATED
 *   8. Cache the RCA result in Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RCAService {

    private final ContextGatherer contextGatherer;
    private final PromptBuilder promptBuilder;
    private final OpenRouterClient openRouterClient;
    private final OllamaClient ollamaClient;
    private final LLMCacheService cacheService;
    private final RateLimiter rateLimiter;
    private final IncidentRepository incidentRepository;
    private final AnomalyRecordRepository anomalyRecordRepository;
    private final ObjectMapper objectMapper;

    /**
     * Performs full RCA for a detected anomaly.
     * Called by AnomalyEventConsumer when a message arrives from Kafka.
     */
    @Transactional
    public void performRCA(AnomalyDTO anomaly) {
        UUID anomalyId = UUID.fromString(anomaly.getAnomalyId());
        log.info("🔬 Starting RCA for anomaly={} service={} type={}",
                anomaly.getAnomalyId(), anomaly.getServiceName(), anomaly.getAnomalyType());

        // Guard: Skip if incident already exists for this anomaly (idempotency)
        if (incidentRepository.existsByAnomalyId(anomalyId)) {
            log.info("ℹ️ Incident already exists for anomaly={} — skipping", anomaly.getAnomalyId());
            return;
        }

        // Step 1: Mark anomaly as INVESTIGATING
        anomalyRecordRepository.updateStatus(anomalyId, "INVESTIGATING");
        log.info("📌 Anomaly {} status → INVESTIGATING", anomaly.getAnomalyId());

        Incident incident = null;
        try {
            // Gather log context (always gather so we can save it in the DB)
            List<LogEvent> rawLogs = contextGatherer.gatherRawLogs(anomaly);
            String logContextForPrompt = contextGatherer.formatLogsForPrompt(rawLogs);
            
            String logContextJson = "[]";
            try {
                logContextJson = objectMapper.writeValueAsString(rawLogs);
            } catch (Exception e) {
                log.warn("Failed to serialize raw logs to JSON", e);
            }

            // Phase 1: Create incident early (before AI)
            incident = createInitialIncident(anomaly, logContextJson);

            // Step 2: Check cache
            String cacheKey = cacheService.buildCacheKey(
                    anomaly.getServiceName(),
                    anomaly.getAnomalyType().name(),
                    anomaly.getMetricName()
            );

            RCAResponse rcaResponse = tryGetFromCache(cacheKey);

            if (rcaResponse == null) {
                // Phase 2: Update to ASSESSING
                incident.setStatus("ASSESSING");
                incidentRepository.save(incident);

                // Step 4: Build prompt
                String prompt = promptBuilder.build(anomaly, logContextForPrompt);

                // Step 5: Call LLM (with rate limiting + fallback)
                rcaResponse = callLLMWithFallback(prompt);

                // Step 8: Cache the result
                cacheResponse(cacheKey, rcaResponse);
            }

            // Phase 3: After AI completes
            updateIncidentWithRCA(incident, rcaResponse);
            log.info("✅ Incident saved: id={} rootCause={} confidence={}",
                    incident.getIncidentId(), rcaResponse.getRootCause(), rcaResponse.getConfidence());

            // Step 7: Mark anomaly as INVESTIGATED
            anomalyRecordRepository.updateStatus(anomalyId, "INVESTIGATED");
            log.info("✅ Anomaly {} status → INVESTIGATED", anomaly.getAnomalyId());

        } catch (Exception e) {
            log.error("❌ RCA failed for anomaly={}: {}", anomaly.getAnomalyId(), e.getMessage(), e);
            
            if (incident != null) {
                incident.setStatus("AWAITING_TRIAGE");
                incidentRepository.save(incident);
            } else {
                // Revert status to DETECTED so it can be retried
                anomalyRecordRepository.updateStatus(anomalyId, "DETECTED");
            }
        }
    }

    @Transactional
    public Incident retryRCA(UUID incidentId) {
        Incident incident = incidentRepository.findByIncidentId(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        if (!"AWAITING_TRIAGE".equals(incident.getStatus())) {
            log.info("ℹ️ Incident {} is not AWAITING_TRIAGE. Skipping retry.", incidentId);
            return incident;
        }

        com.sentinel.rca.entity.AnomalyRecord record = anomalyRecordRepository.findByAnomalyId(incident.getAnomalyId())
            .orElseThrow(() -> new IllegalArgumentException("Anomaly not found: " + incident.getAnomalyId()));

        AnomalyDTO anomaly = AnomalyDTO.builder()
            .anomalyId(record.getAnomalyId().toString())
            .serviceName(record.getServiceName())
            .anomalyType(com.sentinel.common.enums.AnomalyType.valueOf(record.getAnomalyType()))
            .severity(com.sentinel.common.enums.Severity.valueOf(record.getSeverity()))
            .metricName(record.getMetricName())
            .detectedAt(record.getDetectedAt())
            .expectedValue(record.getExpectedValue())
            .actualValue(record.getActualValue())
            .zScore(record.getZScore())
            .windowMinutes(record.getWindowMinutes())
            .build();

        incident.setStatus("ASSESSING");
        incidentRepository.save(incident);

        try {
            List<LogEvent> rawLogs = contextGatherer.gatherRawLogs(anomaly);
            String logContextForPrompt = contextGatherer.formatLogsForPrompt(rawLogs);

            String prompt = promptBuilder.build(anomaly, logContextForPrompt);
            RCAResponse rcaResponse = callLLMWithFallback(prompt);

            updateIncidentWithRCA(incident, rcaResponse);
            return incident;
        } catch (Exception e) {
            log.error("❌ Retry RCA failed for incident={}: {}", incidentId, e.getMessage(), e);
            incident.setStatus("AWAITING_TRIAGE");
            incidentRepository.save(incident);
            return incident;
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private RCAResponse tryGetFromCache(String cacheKey) {
        return cacheService.get(cacheKey)
                .map(cached -> {
                    try {
                        RCAResponse r = objectMapper.readValue(cached, RCAResponse.class);
                        log.info("⚡ Using cached RCA response");
                        return r;
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached RCA: {}", e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    private RCAResponse callLLMWithFallback(String prompt) {
        // Check rate limit
        if (!rateLimiter.tryAcquire()) {
            log.warn("⚠️ Rate limit hit — attempting Ollama fallback");
            if (ollamaClient.isAvailable()) {
                return ollamaClient.generateRCA(prompt);
            }
            return buildSkippedResponse("Rate limit exceeded and Ollama not available");
        }

        // Try OpenRouter (primary)
        LLMClient primary = openRouterClient;
        if (primary.isAvailable()) {
            log.info("🌐 Calling {} (primary LLM)", primary.getProviderName());
            RCAResponse response = primary.generateRCA(prompt);
            if (response.isParseSuccess() && response.getConfidence() > 0.0) {
                return response;
            }
            log.warn("OpenRouter response was not successful — trying Ollama fallback");
        } else {
            log.warn("OpenRouter not configured — trying Ollama fallback");
        }

        // Try Ollama (fallback)
        if (ollamaClient.isAvailable()) {
            log.info("🦙 Calling {} (fallback LLM)", ollamaClient.getProviderName());
            return ollamaClient.generateRCA(prompt);
        }

        log.error("❌ Both LLM providers unavailable — generating placeholder RCA");
        return buildSkippedResponse("Both OpenRouter and Ollama are unavailable");
    }

    private Incident createInitialIncident(AnomalyDTO anomaly, String logContext) {
        String generatedIncNumber = String.format("INC%07d", ThreadLocalRandom.current().nextInt(1000000, 10000000));
        
        Incident incident = Incident.builder()
                .incidentId(UUID.randomUUID())
                .incidentNumber(generatedIncNumber)
                .anomalyId(UUID.fromString(anomaly.getAnomalyId()))
                .title("Anomaly detected in " + anomaly.getServiceName() + " — AI analysis pending")
                .severity(anomaly.getSeverity() != null ? anomaly.getSeverity().name() : "P2")
                .status("NEW")
                .serviceName(anomaly.getServiceName())
                .detectedAt(anomaly.getDetectedAt())
                .relatedLogs(logContext)
                .build();

        return incidentRepository.save(incident);
    }

    private void updateIncidentWithRCA(Incident incident, RCAResponse rca) {
        if (rca.isParseSuccess() && rca.getConfidence() > 0) {
            incident.setStatus("RCA_COMPLETE");
            incident.setTitle(rca.getTitle());
            incident.setRcaSummary(rca.getRcaSummary());
            incident.setRootCause(rca.getRootCause());
            incident.setImpactAnalysis(rca.getImpactAnalysis());
            incident.setSuggestedFix(rca.getSuggestedFix());
            incident.setPrevention(rca.getPrevention());
            incident.setConfidence(rca.getConfidence());
        } else {
            incident.setStatus("AWAITING_TRIAGE");
            incident.setRcaSummary(rca.getRcaSummary());
            incident.setRootCause(rca.getRootCause());
            incident.setImpactAnalysis(rca.getImpactAnalysis());
            incident.setSuggestedFix(rca.getSuggestedFix());
            incident.setPrevention(rca.getPrevention());
            incident.setConfidence(rca.getConfidence());
        }
        incident.setAnalyzedAt(LocalDateTime.now());
        incidentRepository.save(incident);
    }

    private void cacheResponse(String cacheKey, RCAResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            cacheService.put(cacheKey, json);
        } catch (Exception e) {
            log.warn("Failed to cache RCA response: {}", e.getMessage());
        }
    }

    private RCAResponse buildSkippedResponse(String reason) {
        return RCAResponse.builder()
                .rootCause("UNKNOWN")
                .title("Automated RCA skipped — manual investigation required")
                .rcaSummary("RCA was not generated: " + reason)
                .rootCauseDetail("Please review related logs manually.")
                .impactAnalysis("Unknown — manual assessment required.")
                .suggestedFix("Configure OPENROUTER_API_KEY in application.yml or start Ollama locally.")
                .prevention("Ensure at least one LLM provider is configured and accessible.")
                .confidence(0.0)
                .parseSuccess(false)
                .build();
    }
}
