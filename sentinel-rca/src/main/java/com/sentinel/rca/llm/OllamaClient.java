package com.sentinel.rca.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.rca.model.RCAResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Ollama LLM client — local fallback when OpenRouter is unavailable.
 *
 * Runs a local LLM (llama3/mistral) via Ollama Docker container.
 * Same prompt format as OpenRouter for transparent fallback.
 */
@Slf4j
@Component
public class OllamaClient implements LLMClient {

    private static final String PROVIDER = "Ollama";
    private static final String GENERATE_ENDPOINT = "/api/chat";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.ollama.base-url}")
    private String baseUrl;

    @Value("${llm.ollama.model}")
    private String model;

    public OllamaClient(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    @Override
    public RCAResponse generateRCA(String prompt) {
        log.info("🦙 Calling Ollama (model={}) as fallback LLM...", model);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", getSystemPrompt()),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "stream", false,
                    "options", Map.of("temperature", 0.2)
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + GENERATE_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            return parseOllamaResponse(response.getBody());

        } catch (ResourceAccessException e) {
            log.error("❌ Ollama not reachable — is it running? Error: {}", e.getMessage());
            return buildFallbackResponse("Ollama not running at " + baseUrl);
        } catch (Exception e) {
            log.error("❌ Ollama call failed: {}", e.getMessage());
            return buildFallbackResponse(e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public boolean isAvailable() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/api/tags", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Ollama availability check failed: {}", e.getMessage());
            return false;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String getSystemPrompt() {
        return """
                You are an expert Site Reliability Engineer (SRE) analyzing production incidents.
                Analyze the provided anomaly and logs, then respond ONLY with a valid JSON object:
                {
                  "rootCause": "DB_OUTAGE|MEMORY_LEAK|DOWNSTREAM_FAILURE|DEPLOYMENT_ISSUE|TRAFFIC_SPIKE|CONFIGURATION_ERROR|NETWORK_ISSUE",
                  "title": "Short incident title (max 80 chars)",
                  "rcaSummary": "2-3 sentence summary",
                  "rootCauseDetail": "Detailed technical explanation with log evidence",
                  "impactAnalysis": "Which users/services are affected",
                  "suggestedFix": "Step by step fix",
                  "prevention": "How to prevent this",
                  "confidence": 0.80
                }
                Respond ONLY with JSON. No markdown, no explanation outside the JSON.
                """;
    }

    private RCAResponse parseOllamaResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("message").path("content").asText();

            // Ollama sometimes wraps JSON in markdown code blocks — strip them
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            JsonNode rcaJson = objectMapper.readTree(content);

            return RCAResponse.builder()
                    .rootCause(rcaJson.path("rootCause").asText("UNKNOWN"))
                    .title(rcaJson.path("title").asText("Incident detected"))
                    .rcaSummary(rcaJson.path("rcaSummary").asText())
                    .rootCauseDetail(rcaJson.path("rootCauseDetail").asText())
                    .impactAnalysis(rcaJson.path("impactAnalysis").asText())
                    .suggestedFix(rcaJson.path("suggestedFix").asText())
                    .prevention(rcaJson.path("prevention").asText())
                    .confidence(rcaJson.path("confidence").asDouble(0.5))
                    .parseSuccess(true)
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to parse Ollama response: {}", e.getMessage());
            return buildFallbackResponse("Parse error: " + e.getMessage());
        }
    }

    private RCAResponse buildFallbackResponse(String reason) {
        return RCAResponse.builder()
                .rootCause("UNKNOWN")
                .title("RCA generation failed — manual investigation required")
                .rcaSummary("Automated RCA via Ollama failed: " + reason)
                .rootCauseDetail("Please investigate manually.")
                .impactAnalysis("Unknown — manual assessment required.")
                .suggestedFix("Check Ollama is running: docker ps | grep ollama")
                .prevention("Ensure Ollama is running with a valid model loaded.")
                .confidence(0.0)
                .parseSuccess(false)
                .build();
    }
}
