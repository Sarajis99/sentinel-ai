package com.sentinel.simulator.generator;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Injects realistic anomaly scenarios into the log stream.
 * Each anomaly type generates a burst of logs that the detector should catch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyInjector {

    private static final String POD_SUFFIX = "-pod-";
    private static final String ANOMALY_KEY = "anomaly";

    private final Random random = new Random();

    public enum AnomalyScenario {
        ERROR_SPIKE,       // Sudden burst of ERROR logs
        LATENCY_SURGE,     // API latency spikes dramatically
        DB_OUTAGE,         // Database connection failures
        MEMORY_LEAK,       // Gradual latency increase + OOM errors
        DOWNSTREAM_FAILURE // External service timeout
    }

    /**
     * Inject an anomaly scenario — returns a burst of 20-50 anomalous log events
     */
    public List<LogEventDTO> injectAnomaly(String serviceName, AnomalyScenario scenario) {
        log.warn("🚨 INJECTING ANOMALY: {} on service: {}", scenario, serviceName);

        return switch (scenario) {
            case ERROR_SPIKE -> generateErrorSpike(serviceName);
            case LATENCY_SURGE -> generateLatencySurge(serviceName);
            case DB_OUTAGE -> generateDbOutage(serviceName);
            case MEMORY_LEAK -> generateMemoryLeak(serviceName);
            case DOWNSTREAM_FAILURE -> generateDownstreamFailure(serviceName);
        };
    }

    /**
     * ERROR_SPIKE: 40-50 ERROR logs in quick succession
     * Normal baseline: ~5% error rate → Spike: ~85% error rate
     */
    private List<LogEventDTO> generateErrorSpike(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();
        int count = 40 + random.nextInt(10);

        for (int i = 0; i < count; i++) {
            events.add(LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now(ZoneId.systemDefault()).plusNanos(i * 100_000_000L))
                    .serviceName(serviceName)
                    .logLevel(LogLevel.ERROR)
                    .message("CRITICAL: Unhandled exception in request processing - " + i)
                    .stackTrace("java.lang.RuntimeException: Unexpected error\n" +
                            "\tat com.sentinel.service.RequestHandler.handle(RequestHandler.java:" + (100 + i) + ")")
                    .requestId(UUID.randomUUID().toString().substring(0, 8))
                    .latencyMs(5000 + random.nextInt(2000))
                    .statusCode(500)
                    .host(serviceName + POD_SUFFIX + random.nextInt(3))
                    .metadata(Map.of(ANOMALY_KEY, "error_spike", "env", "prod"))
                    .build());
        }
        return events;
    }

    /**
     * LATENCY_SURGE: Latency jumps from ~100ms to 3000-8000ms
     */
    private List<LogEventDTO> generateLatencySurge(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();
        int count = 30 + random.nextInt(10);

        for (int i = 0; i < count; i++) {
            int spikedLatency = 3000 + random.nextInt(5000);
            events.add(LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now(ZoneId.systemDefault()).plusNanos(i * 200_000_000L))
                    .serviceName(serviceName)
                    .logLevel(i % 5 == 0 ? LogLevel.ERROR : LogLevel.WARN)
                    .message(String.format("WARN: Request processing slow - latency=%dms (threshold=500ms)", spikedLatency))
                    .requestId(UUID.randomUUID().toString().substring(0, 8))
                    .latencyMs(spikedLatency)
                    .statusCode(i % 10 == 0 ? 503 : 200)
                    .host(serviceName + POD_SUFFIX + random.nextInt(3))
                    .metadata(Map.of(ANOMALY_KEY, "latency_surge", "env", "prod"))
                    .build());
        }
        return events;
    }

    /**
     * DB_OUTAGE: Database connection failures cascade across all pods
     */
    private List<LogEventDTO> generateDbOutage(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();

        String dbErrorMessage = "FATAL: Cannot acquire database connection from pool - pool exhausted";
        String dbStackTrace = """
                org.springframework.dao.DataAccessResourceFailureException: Unable to acquire JDBC Connection
                \tat org.hibernate.engine.jdbc.connections.internal.ConnectionProviderInitiator.initiateService
                \tat com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:213)
                \tat com.sentinel.repository.OrderRepository.findById(OrderRepository.java:45)""";

        for (int i = 0; i < 35; i++) {
            events.add(LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now(ZoneId.systemDefault()).plusNanos(i * 150_000_000L))
                    .serviceName(serviceName)
                    .logLevel(LogLevel.ERROR)
                    .message(dbErrorMessage)
                    .stackTrace(dbStackTrace)
                    .requestId(UUID.randomUUID().toString().substring(0, 8))
                    .latencyMs(30000) // 30s timeout
                    .statusCode(503)
                    .host(serviceName + POD_SUFFIX + (i % 3))
                    .metadata(Map.of(ANOMALY_KEY, "db_outage", "env", "prod"))
                    .build());
        }
        return events;
    }

    /**
     * MEMORY_LEAK: Gradual latency increase with eventual OOM error
     */
    private List<LogEventDTO> generateMemoryLeak(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            int latency = 200 + (i * 150); // Gradual increase
            boolean isOOM = i == 24;

            events.add(LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now(ZoneId.systemDefault()).plusNanos(i * 300_000_000L))
                    .serviceName(serviceName)
                    .logLevel(isOOM ? LogLevel.ERROR : LogLevel.WARN)
                    .message(isOOM
                            ? "FATAL: java.lang.OutOfMemoryError: Java heap space"
                            : String.format("WARN: Heap usage at %d%%, latency degrading: %dms",
                            60 + (i * 2), latency))
                    .stackTrace(isOOM ? "java.lang.OutOfMemoryError: Java heap space\n" +
                            "\tat java.util.Arrays.copyOf(Arrays.java:3210)" : null)
                    .latencyMs(latency)
                    .statusCode(isOOM ? 500 : 200)
                    .host(serviceName + POD_SUFFIX + "0") // Single pod leaking
                    .metadata(Map.of(ANOMALY_KEY, "memory_leak", "env", "prod"))
                    .build());
        }
        return events;
    }

    /**
     * DOWNSTREAM_FAILURE: External service timeouts cascade
     */
    private List<LogEventDTO> generateDownstreamFailure(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            events.add(LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now(ZoneId.systemDefault()).plusNanos(i * 200_000_000L))
                    .serviceName(serviceName)
                    .logLevel(i % 3 == 0 ? LogLevel.ERROR : LogLevel.WARN)
                    .message(String.format("Downstream service timeout after %dms - attempt %d/3",
                            5000 + random.nextInt(3000), (i % 3) + 1))
                    .stackTrace(i % 3 == 0 ?
                            "java.net.SocketTimeoutException: Read timed out\n" +
                            "\tat sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:278)" : null)
                    .latencyMs(5000 + random.nextInt(3000))
                    .statusCode(i % 3 == 0 ? 503 : 504)
                    .host(serviceName + POD_SUFFIX + random.nextInt(3))
                    .metadata(Map.of(ANOMALY_KEY, "downstream_failure", "env", "prod"))
                    .build());
        }
        return events;
    }

    public AnomalyScenario pickRandomScenario() {
        AnomalyScenario[] scenarios = AnomalyScenario.values();
        return scenarios[random.nextInt(scenarios.length)];
    }
}
