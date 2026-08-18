package com.sentinel.rca.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.rca.model.RCAResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OpenRouter LLM client — primary AI provider with dynamic Auto-Healing.
 *
 * It uses the configured default model first.
 * On startup, it fetches the live list of all 100% free models from OpenRouter.
 * If the default model fails (e.g., becomes paid or deprecated), it automatically
 * retries the request using the dynamic list of free fallback models.
 */
@Slf4j
@Component
public class OpenRouterClient implements LLMClient {

    private static final String PROVIDER = "OpenRouter";
    private static final String CHAT_ENDPOINT = "/chat/completions";
    private static final String MODELS_ENDPOINT = "/models";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;


    private final StringRedisTemplate redisTemplate;

    @Value("${llm.openrouter.api-key}")
    private String defaultApiKey;

    @Value("${SETTINGS_ENCRYPTION_KEY:sentinel-ai-default-encryption-key-32b}")
    private String encryptionKeyStr;

    @Value("${llm.openrouter.base-url}")
    private String baseUrl;

    @Value("${llm.openrouter.model}")
    private String defaultModel;

    @Value("${llm.openrouter.site-url}")
    private String siteUrl;

    @Value("${llm.openrouter.app-name}")
    private String appName;

    public OpenRouterClient(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    private final List<String> fallbackModels = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (!isAvailable()) return;
        
        // 1. Add our proven, rock-solid curated models FIRST (these cross different upstream providers)
        fallbackModels.add("qwen/qwen-2.5-72b-instruct:free");
        fallbackModels.add("meta-llama/llama-3.3-70b-instruct:free");
        
        // 2. Fetch the dynamic list and append to the back
        fetchFreeModels();
    }

    private void fetchFreeModels() {
        try {
            log.info("🔍 Fetching live free models from OpenRouter...");
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + MODELS_ENDPOINT, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            for (JsonNode modelNode : root.path("data")) {
                JsonNode pricing = modelNode.path("pricing");
                if ("0".equals(pricing.path("prompt").asText()) && "0".equals(pricing.path("completion").asText())) {
                    String modelId = modelNode.path("id").asText();
                    
                    // Don't add if it's already in our curated list, or if it's the default model
                    if (!fallbackModels.contains(modelId) && !modelId.equals(defaultModel)) {
                        // Prioritize fast instruction models by inserting them immediately after our curated models
                        if (modelId.contains("flash") || modelId.contains("instruct") || modelId.contains("it")) {
                            fallbackModels.add(2, modelId); 
                        } else {
                            fallbackModels.add(modelId);    
                        }
                    }
                }
            }
            log.info("✅ Fallback list ready. Top fallbacks: {}, {}", fallbackModels.get(0), fallbackModels.get(1));
            
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch dynamic free models: {}", e.getMessage());
            // It's okay, we already seeded the curated list in init()
        }
    }

    @Override
    public RCAResponse generateRCA(String prompt) {
        return attemptGenerateWithFallback(prompt, defaultModel, 0);
    }

    private RCAResponse attemptGenerateWithFallback(String prompt, String modelToTry, int fallbackIndex) {
        log.info("🤖 Calling OpenRouter LLM (model={}) for RCA...", modelToTry);

        try {
            HttpHeaders headers = buildHeaders();
            Map<String, Object> requestBody = buildRequestBody(prompt, modelToTry);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + CHAT_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            RCAResponse parsedResponse = parseResponse(response.getBody());
            if (!parsedResponse.isParseSuccess()) {
                log.warn("❌ Model {} returned invalid JSON structure, trying fallback", modelToTry);
                return tryNextFallback(prompt, fallbackIndex);
            }
            return parsedResponse;

        } catch (HttpClientErrorException e) {
            // These errors mean the model is likely no longer free, restricted, or doesn't exist
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.FORBIDDEN || e.getStatusCode().value() == 402) {
                log.warn("❌ Model {} is unavailable or no longer free. Error: {}", modelToTry, e.getStatusCode());
                return tryNextFallback(prompt, fallbackIndex);
            }
            log.error("❌ OpenRouter call failed with model {}: {}", modelToTry, e.getMessage());
            return tryNextFallback(prompt, fallbackIndex);
            
        } catch (Exception e) {
            log.error("❌ OpenRouter call failed with model {}: {}", modelToTry, e.getMessage());
            // For general errors (like 500s or timeouts), we can also try a fallback model
            return tryNextFallback(prompt, fallbackIndex);
        }
    }
    
    private RCAResponse tryNextFallback(String prompt, int currentIndex) {
        if (currentIndex < fallbackModels.size() && currentIndex < 4) { // Cap at 4 fallback attempts (5 total LLM calls)
             String nextModel = fallbackModels.get(currentIndex);
             log.info("🔄 Auto-healing: Falling back to alternative free model: {}", nextModel);
             return attemptGenerateWithFallback(prompt, nextModel, currentIndex + 1);
        }
        
        log.error("💥 All fallback models exhausted or unavailable.");
        return buildFallbackResponse("All OpenRouter models failed. Exhausted fallback list.");
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public boolean isAvailable() {
        String key = getEffectiveApiKey();
        return key != null && !key.isBlank() && !key.equals("your-openrouter-key-here");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + getEffectiveApiKey());
        headers.set("HTTP-Referer", siteUrl);
        headers.set("X-Title", appName);
        return headers;
    }

    private Map<String, Object> buildRequestBody(String prompt, String model) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", getSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2,       
                "max_tokens", 1500,
                "response_format", Map.of("type", "json_object")
        );
    }

    private String getSystemPrompt() {
        return """
                You are an expert Site Reliability Engineer (SRE) analyzing production incidents.
                You will be given an anomaly detection alert along with the raw log entries from the affected service.
                
                Your job is to:
                1. Identify the TRUE root cause (not just the symptom — dig deeper)
                2. Assess the impact on users and downstream services
                3. Provide a clear, actionable fix for the on-call engineer
                4. Suggest how to prevent this in the future
                
                You MUST respond ONLY with a valid JSON object in this exact format:
                {
                  "rootCause": "Short category like DB_OUTAGE, MEMORY_LEAK, DOWNSTREAM_FAILURE, DEPLOYMENT_ISSUE, TRAFFIC_SPIKE, CONFIGURATION_ERROR, NETWORK_ISSUE",
                  "title": "Short incident title for the dashboard (max 80 chars)",
                  "rcaSummary": "2-3 sentence plain English summary of what happened and why",
                  "rootCauseDetail": "Detailed technical explanation with evidence from the logs",
                  "impactAnalysis": "Which users and services are affected, and how severely",
                  "suggestedFix": "Step by step fix for the on-call engineer",
                  "prevention": "How to prevent this from happening again",
                  "confidence": 0.85
                }
                
                Do not include any text outside the JSON object. Be specific and reference actual log messages as evidence.
                """;
    }

    private RCAResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            JsonNode rcaJson = objectMapper.readTree(content);

            String summary = rcaJson.path("rcaSummary").asText();
            String rootCause = rcaJson.path("rootCause").asText();
            boolean isValid = summary != null && !summary.isBlank() && !summary.equals("null") &&
                              rootCause != null && !rootCause.isBlank() && !rootCause.equals("UNKNOWN");

            return RCAResponse.builder()
                    .rootCause(rcaJson.path("rootCause").asText("UNKNOWN"))
                    .title(rcaJson.path("title").asText("Incident detected"))
                    .rcaSummary(rcaJson.path("rcaSummary").asText())
                    .rootCauseDetail(rcaJson.path("rootCauseDetail").asText())
                    .impactAnalysis(rcaJson.path("impactAnalysis").asText())
                    .suggestedFix(rcaJson.path("suggestedFix").asText())
                    .prevention(rcaJson.path("prevention").asText())
                    .confidence(rcaJson.path("confidence").asDouble(0.5))
                    .parseSuccess(isValid)
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to parse OpenRouter response: {}", e.getMessage());
            return buildFallbackResponse("Parse error: " + e.getMessage());
        }
    }

    private RCAResponse buildFallbackResponse(String reason) {
        return RCAResponse.builder()
                .rootCause("UNKNOWN")
                .title("RCA generation failed — manual investigation required")
                .rcaSummary("Automated RCA could not be generated: " + reason)
                .rootCauseDetail("Please investigate manually using the related logs.")
                .impactAnalysis("Unknown — manual assessment required.")
                .suggestedFix("Review logs manually and investigate the root cause.")
                .prevention("Ensure OpenRouter API key is configured and valid.")
                .confidence(0.0)
                .parseSuccess(false)
                .build();
    }

    private String getEffectiveApiKey() {
        try {
            String encodedKey = redisTemplate.opsForValue().get("settings:openrouter-api-key");
            if (encodedKey != null && !encodedKey.isBlank()) {
                byte[] encryptedData = Base64.getDecoder().decode(encodedKey);
                byte[] iv = new byte[12];
                System.arraycopy(encryptedData, 0, iv, 0, iv.length);

                byte[] ciphertext = new byte[encryptedData.length - iv.length];
                System.arraycopy(encryptedData, iv.length, ciphertext, 0, ciphertext.length);

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] keyBytes = digest.digest(encryptionKeyStr.getBytes(StandardCharsets.UTF_8));
                SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);

                byte[] plainText = cipher.doFinal(ciphertext);
                return new String(plainText, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to decrypt API key from Redis, falling back to default.", e);
        }
        return defaultApiKey;
    }
}
