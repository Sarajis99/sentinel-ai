package com.sentinel.api.service;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SimulationService — manages the continuous normal traffic generation.
 *
 * When triggered:
 * 1. Acquires a Redis lock (prevents concurrent simulations) with 15 min TTL
 * 2. Starts scheduled normal traffic generation based on logsPerSecond setting
 *
 * When stopped:
 * 1. Cancels scheduled task
 * 2. Deletes Redis lock
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, LogEventDTO> kafkaTemplate;

    private static final String LOCK_KEY = "simulator:active_lock";
    private static final String LOGS_PER_SECOND_KEY = "simulator:logs-per-second";
    private static final String TOPIC = "log-events";
    private static final List<String> SERVICES = List.of(
            "payment-service", "order-service", "inventory-service",
            "notification-service", "user-service"
    );

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();
    private ScheduledFuture<?> simulationTask;
    private ScheduledFuture<?> autoStopTask;

    /**
     * Attempt to start a simulation. Returns true if started, false if one is already running.
     */
    public boolean triggerSimulation() {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "running", Duration.ofMinutes(15));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("⚠️ Simulation already active — rejecting concurrent request");
            return false;
        }

        int logsPerSecond = 5;
        String logsPerSecondStr = redisTemplate.opsForValue().get(LOGS_PER_SECOND_KEY);
        if (logsPerSecondStr != null) {
            try {
                logsPerSecond = Integer.parseInt(logsPerSecondStr);
            } catch (NumberFormatException ignored) {}
        }
        long periodMs = 1000L / Math.max(1, logsPerSecond);

        log.info("🚀 === SIMULATION STARTED === Normal traffic at {} logs/sec ({} ms interval). Lock acquired for 15m", logsPerSecond, periodMs);

        simulationTask = executor.scheduleAtFixedRate(this::generateSingleEvent, 0, periodMs, TimeUnit.MILLISECONDS);
        autoStopTask = executor.schedule(() -> {
            log.info("⏳ 15-minute safety timeout reached. Auto-stopping simulation.");
            stopSimulation();
        }, 15, TimeUnit.MINUTES);

        return true;
    }

    /**
     * Stop the running simulation.
     */
    public boolean stopSimulation() {
        boolean wasActive = isSimulationActive();

        if (simulationTask != null) {
            simulationTask.cancel(false);
            simulationTask = null;
        }
        if (autoStopTask != null) {
            autoStopTask.cancel(false);
            autoStopTask = null;
        }

        redisTemplate.delete(LOCK_KEY);
        
        if (wasActive) {
            log.info("🛑 === SIMULATION STOPPED ===");
        }
        
        // Return true if we actually stopped something (either the lock was active or tasks were running)
        return wasActive || simulationTask != null || autoStopTask != null;
    }

    /**
     * Check if a simulation is currently running.
     */
    public boolean isSimulationActive() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY));
    }

    /**
     * Generate and send a single normal log event.
     */
    private void generateSingleEvent() {
        try {
            String service = SERVICES.get(random.nextInt(SERVICES.size()));
            
            // 1. Organic Anomaly (Micro-burst) - 0.01% chance
            boolean isAnomaly = random.nextDouble() < 0.0001;
            
            // 2. Breathing Latency (Sine Wave)
            double timeMinutes = System.currentTimeMillis() / 60000.0;
            double offset = Math.abs(service.hashCode()) % 10;
            double wave = Math.sin((timeMinutes / 2.0) + offset);
            double multiplier = 1.0 + (wave * 0.8); // 0.2 to 1.8
            
            boolean isWarn = random.nextDouble() < 0.05;
            
            LogLevel level = isWarn ? LogLevel.WARN : LogLevel.INFO;
            String message = isWarn 
                    ? "WARN: Request processed with slight delay or retry - requestId=" + UUID.randomUUID().toString().substring(0, 8)
                    : "Normal request processed successfully - requestId=" + UUID.randomUUID().toString().substring(0, 8);
                    
            int latencyMs = (int) ((30 + random.nextInt(60)) * multiplier) + (isWarn ? random.nextInt(50) : 0);
            
            // Override if it's an organic anomaly
            if (isAnomaly) {
                // To actually shift the P99 latency on the dashboard and make the node turn Red/Yellow,
                // we must send a micro-burst of errors (since a single log won't affect the 99th percentile of 600 logs)
                for (int i = 0; i < 15; i++) {
                    LogEventDTO anomalyEvent = LogEventDTO.builder()
                            .eventId(UUID.randomUUID().toString())
                            .timestamp(LocalDateTime.now())
                            .serviceName(service)
                            .logLevel(LogLevel.ERROR)
                            .message("ERROR: Intermittent network timeout - organic anomaly - requestId=" + UUID.randomUUID().toString().substring(0, 8))
                            .latencyMs(800 + random.nextInt(500))
                            .statusCode(500)
                            .host(service + "-pod-" + random.nextInt(3))
                            .metadata(Map.of("env", "prod", "simulation", "true"))
                            .build();
                    kafkaTemplate.send(TOPIC, service, anomalyEvent);
                }
                return; // Skip sending the normal event this cycle
            }
            
            LogEventDTO event = LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now())
                    .serviceName(service)
                    .logLevel(level)
                    .message(message)
                    .latencyMs(latencyMs)
                    .statusCode(200)
                    .host(service + "-pod-" + random.nextInt(3))
                    .metadata(Map.of("env", "prod", "simulation", "true"))
                    .build();
            kafkaTemplate.send(TOPIC, service, event);
        } catch (Exception e) {
            log.error("Error generating simulation event", e);
        }
    }
}
