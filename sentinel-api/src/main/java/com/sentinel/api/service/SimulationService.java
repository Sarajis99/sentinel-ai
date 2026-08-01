package com.sentinel.api.service;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SimulationService — manages the Global Lock + 2-minute time-boxed simulation.
 *
 * When triggered:
 * 1. Acquires a Redis lock (prevents concurrent simulations)
 * 2. Sends normal traffic for 30 seconds
 * 3. Injects a massive ERROR spike (the anomaly)
 * 4. Returns to normal traffic for remaining time
 * 5. Auto-stops after 2 minutes, lock expires
 *
 * This follows the industry-standard "Shared Staging + Chaos Engineering" pattern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, LogEventDTO> kafkaTemplate;

    private static final String LOCK_KEY = "simulator:active_lock";
    private static final String TOPIC = "log-events";
    private static final List<String> SERVICES = List.of(
            "payment-service", "order-service", "inventory-service",
            "notification-service", "user-service"
    );

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    @Value("${simulation.lock-duration-seconds:150}")
    private int lockDurationSeconds;

    /**
     * Attempt to start a simulation. Returns true if started, false if one is already running.
     */
    public boolean triggerSimulation() {
        // Try to acquire global lock
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "running", Duration.ofSeconds(lockDurationSeconds));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("⚠️ Simulation already active — rejecting concurrent request");
            return false;
        }

        log.info("🚀 === SIMULATION STARTED === Lock acquired for {}s", lockDurationSeconds);

        // Phase 1 (0-30s): Normal traffic background
        executor.submit(this::sendNormalTrafficBurst);

        // Phase 2 (30s): Inject anomaly spike
        executor.schedule(this::injectAnomalyBurst, 30, TimeUnit.SECONDS);

        // Phase 3 (31-120s): Normal traffic continues
        executor.schedule(this::sendNormalTrafficBurst, 35, TimeUnit.SECONDS);

        // Auto-cleanup after simulation ends
        executor.schedule(() -> {
            log.info("🏁 === SIMULATION COMPLETE === Auto-stopped after 2 minutes");
        }, lockDurationSeconds, TimeUnit.SECONDS);

        return true;
    }

    /**
     * Check if a simulation is currently running.
     */
    public boolean isSimulationActive() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY));
    }

    /**
     * Send a burst of normal traffic events.
     */
    private void sendNormalTrafficBurst() {
        log.info("📡 Sending normal traffic burst...");
        for (int i = 0; i < 150; i++) {
            String service = SERVICES.get(random.nextInt(SERVICES.size()));
            LogEventDTO event = LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now())
                    .serviceName(service)
                    .logLevel(LogLevel.INFO)
                    .message("Normal request processed successfully - requestId=" + UUID.randomUUID().toString().substring(0, 8))
                    .latencyMs(50 + random.nextInt(150))
                    .statusCode(200)
                    .host(service + "-pod-" + random.nextInt(3))
                    .metadata(Map.of("env", "prod", "simulation", "true"))
                    .build();
            kafkaTemplate.send(TOPIC, service, event);

            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /**
     * Inject a massive ERROR spike — the anomaly that the detector should catch.
     */
    private void injectAnomalyBurst() {
        String targetService = SERVICES.get(random.nextInt(SERVICES.size()));
        log.warn("🚨 === ANOMALY INJECTION === Targeting: {}", targetService);

        for (int i = 0; i < 50; i++) {
            LogEventDTO event = LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now())
                    .serviceName(targetService)
                    .logLevel(LogLevel.ERROR)
                    .message("CRITICAL: Unhandled exception in request processing - cascade failure #" + i)
                    .stackTrace("java.lang.RuntimeException: Unexpected error\n" +
                            "\tat com.sentinel.service.RequestHandler.handle(RequestHandler.java:" + (100 + i) + ")")
                    .requestId(UUID.randomUUID().toString().substring(0, 8))
                    .latencyMs(5000 + random.nextInt(3000))
                    .statusCode(500)
                    .host(targetService + "-pod-" + random.nextInt(3))
                    .metadata(Map.of("env", "prod", "anomaly", "error_spike", "simulation", "true"))
                    .build();
            kafkaTemplate.send(TOPIC, targetService, event);
        }

        log.warn("🚨 === ANOMALY INJECTION COMPLETE === Sent 50 ERROR events to {}", targetService);
    }
}
