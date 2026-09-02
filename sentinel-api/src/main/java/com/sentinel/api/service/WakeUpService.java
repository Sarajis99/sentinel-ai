package com.sentinel.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class WakeUpService {

    private final RestTemplate restTemplate;

    @Value("${INGESTION_URL:http://localhost:8081}")
    private String ingestionUrl;

    @Value("${DETECTOR_URL:http://localhost:8082}")
    private String detectorUrl;

    @Value("${RCA_URL:http://localhost:8083}")
    private String rcaUrl;

    public WakeUpService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(60)) // Allow 60s for Render to wake up
                .build();
    }

    public Map<String, String> wakeAllServices() {
        // Ping all 3 services asynchronously
        CompletableFuture<String> ingestionFuture = pingService(ingestionUrl);
        CompletableFuture<String> detectorFuture = pingService(detectorUrl);
        CompletableFuture<String> rcaFuture = pingService(rcaUrl);

        // Wait a maximum of 1.5 seconds for responses so the UI doesn't freeze
        Map<String, String> statuses = new HashMap<>();
        statuses.put("sentinel-ingestion", resolveQuickly(ingestionFuture));
        statuses.put("sentinel-detector", resolveQuickly(detectorFuture));
        statuses.put("sentinel-rca", resolveQuickly(rcaFuture));

        return statuses;
    }

    private String resolveQuickly(CompletableFuture<String> future) {
        try {
            return future.get(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return "STARTING"; // Still booting in the background thread
        } catch (Exception e) {
            return "STARTING"; 
        }
    }

    private CompletableFuture<String> pingService(String baseUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                restTemplate.getForObject(baseUrl + "/ping", Map.class);
                return "UP";
            } catch (Exception e) {
                return "STARTING";
            }
        });
    }
}
