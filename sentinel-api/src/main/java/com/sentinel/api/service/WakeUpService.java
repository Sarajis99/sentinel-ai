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
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Map<String, String> wakeAllServices() {
        CompletableFuture<String> ingestionFuture = pingService(ingestionUrl);
        CompletableFuture<String> detectorFuture = pingService(detectorUrl);
        CompletableFuture<String> rcaFuture = pingService(rcaUrl);

        CompletableFuture.allOf(ingestionFuture, detectorFuture, rcaFuture).join();

        Map<String, String> statuses = new HashMap<>();
        statuses.put("sentinel-ingestion", ingestionFuture.join());
        statuses.put("sentinel-detector", detectorFuture.join());
        statuses.put("sentinel-rca", rcaFuture.join());

        return statuses;
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
