package com.sentinel.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * WakeUpService — Wakes sleeping Render free-tier services.
 *
 * Design rationale:
 *   Render free-tier services sleep after 15 minutes of inactivity.
 *   When a request hits a sleeping service, Render's proxy accepts the TCP
 *   connection immediately (within ~1s) and holds it open while the container
 *   boots (2-3 minutes). The key insight is: Render starts booting the moment
 *   it accepts the connection, regardless of whether we wait for the response.
 *
 *   We use a "fire-and-forget" pattern:
 *   1. Send the HTTP request with a long enough timeout for Render's proxy
 *      to accept the TCP connection (~10s covers cold proxy startup).
 *   2. Return "STARTING" to the caller immediately (within ~2s).
 *   3. The background thread keeps the connection open for up to 4 minutes,
 *      ensuring Render completes the boot. The thread runs on a dedicated
 *      executor so it doesn't exhaust the ForkJoinPool.
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

    // Dedicated thread pool so long-running wake-up connections don't starve
    // the shared ForkJoinPool used by CompletableFuture.supplyAsync().
    private final ExecutorService wakeUpExecutor = Executors.newFixedThreadPool(3);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)) // Render proxy accepts within ~1-2s
            .build();

    /**
     * Pings all 3 downstream services concurrently.
     * Returns immediately with current status (UP or STARTING).
     * Background threads keep connections alive so Render completes the boot.
     */
    public Map<String, String> wakeAllServices() {
        CompletableFuture<String> ingestionFuture = pingService(ingestionUrl, "sentinel-ingestion");
        CompletableFuture<String> detectorFuture  = pingService(detectorUrl,  "sentinel-detector");
        CompletableFuture<String> rcaFuture       = pingService(rcaUrl,       "sentinel-rca");

        // Give up to 2 seconds for already-awake services to respond instantly.
        // Sleeping services will return "STARTING" and continue booting in background.
        Map<String, String> statuses = new HashMap<>();
        statuses.put("sentinel-ingestion", resolveQuickly(ingestionFuture));
        statuses.put("sentinel-detector",  resolveQuickly(detectorFuture));
        statuses.put("sentinel-rca",       resolveQuickly(rcaFuture));

        return statuses;
    }

    private String resolveQuickly(CompletableFuture<String> future) {
        try {
            return future.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return "STARTING";
        }
    }

    private CompletableFuture<String> pingService(String baseUrl, String serviceName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/ping"))
                        .timeout(Duration.ofMinutes(4)) // Patient enough for full Render boot
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    log.info("✅ {} is UP", serviceName);
                    return "UP";
                } else {
                    log.warn("⚠️ {} returned status {}", serviceName, response.statusCode());
                    return "STARTING";
                }
            } catch (Exception e) {
                log.debug("⏳ {} is still booting: {}", serviceName, e.getMessage());
                return "STARTING";
            }
        }, wakeUpExecutor); // Use dedicated executor, not ForkJoinPool
    }
}
