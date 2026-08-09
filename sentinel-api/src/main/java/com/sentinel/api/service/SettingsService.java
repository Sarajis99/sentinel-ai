package com.sentinel.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${SETTINGS_ENCRYPTION_KEY:sentinel-ai-default-encryption-key-32b}")
    private String encryptionKeyStr;

    private static final String API_KEY_REDIS_KEY = "settings:openrouter-api-key";
    private static final String LOGS_PER_SECOND_KEY = "simulator:logs-per-second";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BIT = 128;
    private static final int GCM_IV_LENGTH_BYTE = 12;

    public void updateApiKey(String rawKey) {
        try {
            byte[] keyBytes = getDerivedKey(encryptionKeyStr);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH_BYTE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);

            byte[] ciphertext = cipher.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
            byte[] encryptedData = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, encryptedData, 0, iv.length);
            System.arraycopy(ciphertext, 0, encryptedData, iv.length, ciphertext.length);

            String encodedKey = Base64.getEncoder().encodeToString(encryptedData);
            redisTemplate.opsForValue().set(API_KEY_REDIS_KEY, encodedKey);
            log.info("API Key encrypted and saved to Redis.");
        } catch (Exception e) {
            log.error("Failed to encrypt API key", e);
            throw new RuntimeException("Encryption failed");
        }
    }

    public String getApiKey() {
        String encodedKey = redisTemplate.opsForValue().get(API_KEY_REDIS_KEY);
        if (encodedKey == null) {
            return null;
        }

        try {
            byte[] encryptedData = Base64.getDecoder().decode(encodedKey);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTE];
            System.arraycopy(encryptedData, 0, iv, 0, iv.length);

            byte[] ciphertext = new byte[encryptedData.length - iv.length];
            System.arraycopy(encryptedData, iv.length, ciphertext, 0, ciphertext.length);

            byte[] keyBytes = getDerivedKey(encryptionKeyStr);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);

            byte[] plainText = cipher.doFinal(ciphertext);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt API key", e);
            return null; // Don't crash, just treat as missing/invalid
        }
    }

    public String getMaskedApiKey() {
        String key = getApiKey();
        if (key == null || key.isBlank()) return null;
        if (key.length() <= 10) return "sk-***";
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    public Map<String, Object> testLLMConnection() {
        String key = getApiKey();
        if (key == null) {
            return Map.of("success", false, "message", "No API key configured");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + key);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://openrouter.ai/api/v1/auth/key",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return Map.of("success", true, "message", "Connection successful!");
            } else {
                return Map.of("success", false, "message", "API returned status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    public void factoryReset() {
        log.warn("⚠️ FACTORY RESET INITIATED");
        
        try {
            jdbcTemplate.execute("TRUNCATE TABLE incident_comments, incidents, anomalies, log_events CASCADE");
        } catch (Exception e) {
            log.error("Failed to truncate tables", e);
        }

        String[] patterns = {"rca:*", "metrics:*", "simulator:*"};
        for (String pattern : patterns) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        
        log.info("✅ Factory reset completed");
    }

    public Map<String, Object> getSimulationConfig() {
        String val = redisTemplate.opsForValue().get(LOGS_PER_SECOND_KEY);
        int logsPerSecond = 5;
        if (val != null) {
            try {
                logsPerSecond = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }
        return Map.of("logsPerSecond", logsPerSecond);
    }

    public void updateSimulationConfig(int logsPerSecond) {
        if (logsPerSecond < 1 || logsPerSecond > 20) {
            throw new IllegalArgumentException("logsPerSecond must be between 1 and 20");
        }
        redisTemplate.opsForValue().set(LOGS_PER_SECOND_KEY, String.valueOf(logsPerSecond));
        log.info("Simulation config updated: logsPerSecond = {}", logsPerSecond);
    }

    private byte[] getDerivedKey(String rawKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
    }
}
