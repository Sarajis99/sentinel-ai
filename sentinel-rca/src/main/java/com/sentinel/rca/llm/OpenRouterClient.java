package com.sentinel.rca.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.rca.model.RCAResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * OpenRouter LLM client — primary AI provider with dynamic Auto-Healing.
 *
 * Supports:
 * 1. Multi-provider fallbacks (NVIDIA, MiniMax, Google AI Studio) to prevent single-provider 429 pool outages.
 * 2. Adaptive structured outputs: tries response_format: json_object first, gracefully retrying without it
 *    if the upstream model returns 400 (unsupported feature: structured-outputs).
 * 3. Resilient JSON extraction and fuzzy field mapping to ensure all incident fields are 100% populated in UI.
 * 4. Fast connection/read timeouts to prevent system lag.
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5s connect timeout
        factory.setReadTimeout(12000);    // 12s read timeout
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    private final List<String> fallbackModels = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (!isAvailable()) return;
        
        // Curated cross-provider fallback models (MiniMax, Google, Liquid, NVIDIA)
        List<String> curated = List.of(
            "minimax/minimax-m3:free",
            "google/gemma-4-26b-a4b-it:free",
            "google/gemma-4-31b-it:free",
            "liquid/lfm-2.5-2.6b:free",
            "nvidia/nemotron-3.5-lightning:free"
        );
        for (String m : curated) {
            if (!m.equals(defaultModel) && !fallbackModels.contains(m)) {
                fallbackModels.add(m);
            }
        }
        
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
                    
                    if (!fallbackModels.contains(modelId) && !modelId.equals(defaultModel)) {
                        if (modelId.contains("lightning") || modelId.contains("instruct") || modelId.contains("it")) {
                            fallbackModels.add(Math.min(2, fallbackModels.size()), modelId); 
                        } else {
                            fallbackModels.add(modelId);    
                        }
                    }
                }
            }
            log.info("✅ Fallback list ready. Top fallbacks: {}, {}", fallbackModels.get(0), fallbackModels.get(1));
            
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch dynamic free models: {}", e.getMessage());
        }
    }

    @Override
    public RCAResponse generateRCA(String prompt) {
        return attemptGenerateWithFallback(prompt, defaultModel, true, 0);
    }

    private RCAResponse attemptGenerateWithFallback(String prompt, String modelToTry, boolean requireStructuredJson, int fallbackIndex) {
        log.info("🤖 Calling OpenRouter LLM (model={}, structured={}) for RCA...", modelToTry, requireStructuredJson);

        try {
            HttpHeaders headers = buildHeaders();
            Map<String, Object> requestBody = buildRequestBody(prompt, modelToTry, requireStructuredJson);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + CHAT_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            RCAResponse parsedResponse = parseResponse(response.getBody());
            if (!parsedResponse.isParseSuccess()) {
                log.warn("❌ Model {} returned invalid structure, trying fallback", modelToTry);
                return tryNextFallback(prompt, fallbackIndex);
            }
            return parsedResponse;

        } catch (HttpClientErrorException e) {
            // Adaptive retry: if model rejects structured-outputs with 400, immediately retry WITHOUT response_format
            if (requireStructuredJson && e.getStatusCode() == HttpStatus.BAD_REQUEST 
                    && e.getResponseBodyAsString().contains("structured-outputs")) {
                log.warn("⚠️ Model {} does not support structured-outputs. Retrying with prompt-only JSON enforcement...", modelToTry);
                return attemptGenerateWithFallback(prompt, modelToTry, false, fallbackIndex);
            }

            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.FORBIDDEN || e.getStatusCode().value() == 402) {
                log.warn("❌ Model {} is unavailable or no longer free. Error: {}", modelToTry, e.getStatusCode());
                return tryNextFallback(prompt, fallbackIndex);
            }
            log.error("❌ OpenRouter call failed with model {}: {}", modelToTry, e.getMessage());
            return tryNextFallback(prompt, fallbackIndex);
            
        } catch (Exception e) {
            log.error("❌ OpenRouter call failed with model {}: {}", modelToTry, e.getMessage());
            return tryNextFallback(prompt, fallbackIndex);
        }
    }
    
    private RCAResponse tryNextFallback(String prompt, int currentIndex) {
        if (currentIndex < fallbackModels.size() && currentIndex < 3) { // Cap at 3 fallback attempts (max 4 total calls)
             String nextModel = fallbackModels.get(currentIndex);
             log.info("🔄 Auto-healing: Falling back to alternative free model: {}", nextModel);
             return attemptGenerateWithFallback(prompt, nextModel, true, currentIndex + 1);
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

    private Map<String, Object> buildRequestBody(String prompt, String model, boolean requireStructuredJson) {
        String fullUserPrompt = prompt + "\n\n" + getJsonSchemaInstruction();
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", getSystemPrompt()),
                Map.of("role", "user", "content", fullUserPrompt)
        ));
        body.put("temperature", 0.2);
        body.put("max_tokens", 1500);
        if (requireStructuredJson) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private String getSystemPrompt() {
        return """
                You are an expert Site Reliability Engineer (SRE) analyzing production incidents.
                You will be given an anomaly detection alert along with the raw log entries from the affected service.
                
                Your job is to:
                1. Identify the TRUE root cause (not just the symptom — dig deeper)
                2. Assess the impact on users and downstream services
                3. Provide a clear, actionable fix for the on-call engineer based on the logs
                4. Suggest how to prevent this in the future
                
                You MUST respond ONLY with a valid JSON object in this exact format. Every field is mandatory:
                {
                  "rootCause": "Short category like DB_OUTAGE, MEMORY_LEAK, DOWNSTREAM_FAILURE, DEPLOYMENT_ISSUE, TRAFFIC_SPIKE, CONFIGURATION_ERROR, NETWORK_ISSUE",
                  "title": "Short incident title for the dashboard (max 80 chars)",
                  "rcaSummary": "2-3 sentence plain English summary of what happened and why based on the logs",
                  "rootCauseDetail": "Detailed technical explanation with evidence from the logs",
                  "impactAnalysis": "Which users and services are affected, and how severely",
                  "suggestedFix": "Step by step fix for the on-call engineer based on these logs",
                  "prevention": "How to prevent this from happening again",
                  "confidence": 0.85
                }
                
                Do not include any text outside the JSON object. Be specific and reference actual log messages as evidence.
                """;
    }

    private String getJsonSchemaInstruction() {
        return """
                You MUST respond ONLY with a valid JSON object in this exact format. Every field is mandatory:
                {
                  "rootCause": "Short category like DB_OUTAGE, MEMORY_LEAK, DOWNSTREAM_FAILURE, DEPLOYMENT_ISSUE, TRAFFIC_SPIKE, CONFIGURATION_ERROR, NETWORK_ISSUE",
                  "title": "Short incident title for the dashboard (max 80 chars)",
                  "rcaSummary": "2-3 sentence plain English summary of what happened and why based on the logs",
                  "rootCauseDetail": "Detailed technical explanation with evidence from the logs",
                  "impactAnalysis": "Which users and services are affected, and how severely",
                  "suggestedFix": "Step by step fix for the on-call engineer based on these logs",
                  "prevention": "How to prevent this from happening again",
                  "confidence": 0.85
                }
                Do not include any text, markdown backticks, or preamble outside the JSON object.
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

            // Extract pure JSON between first '{' and last '}' to strip markdown fences or chat filler
            String cleanJson = extractJsonObject(content);
            if (cleanJson == null || cleanJson.isBlank()) {
                log.warn("❌ Could not extract valid JSON object from LLM response");
                return buildFallbackResponse("No valid JSON object found in response");
            }

            JsonNode rcaJson = objectMapper.readTree(cleanJson);

            // Fuzzy field extraction supporting camelCase and snake_case aliases
            String rootCause = getField(rcaJson, "rootCause", "root_cause", "cause");
            String rcaSummary = getField(rcaJson, "rcaSummary", "rca_summary", "summary");
            String impactAnalysis = getField(rcaJson, "impactAnalysis", "impact_analysis", "impact");
            String suggestedFix = getField(rcaJson, "suggestedFix", "suggested_fix", "fix", "mitigation");
            String prevention = getField(rcaJson, "prevention", "prevention_steps", "preventive_measures");
            String title = getField(rcaJson, "title", "incident_title");

            // Quality gate: require authentic AI generation for all core SRE fields!
            // No fake generic advice — if the model failed to provide actual fix, impact, or cause, reject it!
            boolean isValid = rootCause != null && !rootCause.isBlank() && !"UNKNOWN".equalsIgnoreCase(rootCause)
                    && rcaSummary != null && !rcaSummary.isBlank()
                    && suggestedFix != null && !suggestedFix.isBlank()
                    && impactAnalysis != null && !impactAnalysis.isBlank();

            if (!isValid) {
                log.warn("❌ Model failed quality gate: missing authentic rootCause, summary, suggestedFix, or impactAnalysis. Falling back...");
                return buildFallbackResponse("Missing required authentic RCA fields from model");
            }

            if (title == null || title.isBlank()) {
                title = rootCause + " Incident detected";
            }
            if (prevention == null || prevention.isBlank()) {
                prevention = "Review alert thresholds and telemetry logs for " + rootCause;
            }

            double confidence = 0.85;
            if (rcaJson.has("confidence") && rcaJson.get("confidence").isNumber()) {
                confidence = Math.max(0.60, Math.min(0.99, rcaJson.get("confidence").asDouble()));
            }

            return RCAResponse.builder()
                    .rootCause(rootCause.toUpperCase().trim())
                    .title(title)
                    .rcaSummary(rcaSummary)
                    .rootCauseDetail(getField(rcaJson, "rootCauseDetail", "root_cause_detail", "detail"))
                    .impactAnalysis(impactAnalysis)
                    .suggestedFix(suggestedFix)
                    .prevention(prevention)
                    .confidence(confidence)
                    .parseSuccess(true)
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to parse OpenRouter response: {}", e.getMessage());
            return buildFallbackResponse("Parse error: " + e.getMessage());
        }
    }

    private String getField(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            if (node.hasNonNull(name) && !node.path(name).asText().isBlank() && !"null".equalsIgnoreCase(node.path(name).asText())) {
                return node.path(name).asText();
            }
        }
        return null;
    }

    private String extractJsonObject(String text) {
        if (text == null) return null;
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return null;
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
