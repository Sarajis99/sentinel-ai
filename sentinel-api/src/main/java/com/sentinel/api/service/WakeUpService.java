package com.sentinel.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * WakeUpService — Wakes sleeping Render free-tier services.
 *
 * CRITICAL DESIGN: Render rate-limits sleeping services (HTTP 429) if you
 * send too many requests. We must send exactly ONE request per service and
 * hold the connection open patiently until the service boots (~3 minutes).
 *
 * This service tracks in-flight wake-up attempts. If a wake-up is already
 * in progress for a service, subsequent calls just check the status without
 * sending additional HTTP requests.
 */
@Slf4j
@Service
public class WakeUpService {

    @Value("${INGESTION_URL:http://localhost:8081}")
    private String ingestionUrl;

    @Value("${DETECTOR_URL:http://localhost:8082}")
    private String detectorUrl;

    @Value("${RCA_URL:http://localhost:8083}")
    private String rcaUrl;

    // Dedicated thread pool — 3 threads, one per service
    private final ExecutorService wakeUpExecutor = Executors.newFixedThreadPool(3);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Track in-flight wake-up futures so we don't spam Render
    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlightWakeUps = new ConcurrentHashMap<>();

    // Track final statuses
    private final ConcurrentHashMap<String, String> serviceStatuses = new ConcurrentHashMap<>();

    /**
     * Returns current status of all 3 services.
     * On first call, fires ONE wake-up request per service.
     * On subsequent calls, just checks if those requests completed.
     * Never sends duplicate requests while one is in-flight.
     */
    public Map<String, String> wakeAllServices() {
        ensureWakeUpStarted("sentinel-ingestion", ingestionUrl);
        ensureWakeUpStarted("sentinel-detector", detectorUrl);
        ensureWakeUpStarted("sentinel-rca", rcaUrl);

        return Map.of(
                "sentinel-ingestion", serviceStatuses.getOrDefault("sentinel-ingestion", "STARTING"),
                "sentinel-detector", serviceStatuses.getOrDefault("sentinel-detector", "STARTING"),
                "sentinel-rca", serviceStatuses.getOrDefault("sentinel-rca", "STARTING")
        );
    }

    /**
     * Starts a wake-up request ONLY if one isn't already in progress.
     */
    private void ensureWakeUpStarted(String serviceName, String baseUrl) {
        // If already UP, nothing to do
        if ("UP".equals(serviceStatuses.get(serviceName))) {
            return;
        }

        // If a request is already in-flight, don't send another one
        CompletableFuture<String> existing = inFlightWakeUps.get(serviceName);
        if (existing != null && !existing.isDone()) {
            return; // Patient — just wait for the existing request
        }

        // Fire exactly ONE request and track it
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            log.info("🔔 Sending single wake-up ping to {} ...", serviceName);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/ping"))
                        .timeout(Duration.ofMinutes(4)) // Patient: wait for full Render boot
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    log.info("✅ {} is UP (boot complete)", serviceName);
                    serviceStatuses.put(serviceName, "UP");
                    return "UP";
                } else {
                    log.warn("⚠️ {} returned status {} — will retry on next poll",
                            serviceName, response.statusCode());
                    serviceStatuses.put(serviceName, "STARTING");
                    return "STARTING";
                }
            } catch (Exception e) {
                log.debug("⏳ {} wake-up request ended: {}", serviceName, e.getMessage());
                serviceStatuses.put(serviceName, "STARTING");
                return "STARTING";
            }
        }, wakeUpExecutor);

        inFlightWakeUps.put(serviceName, future);
    }
}
