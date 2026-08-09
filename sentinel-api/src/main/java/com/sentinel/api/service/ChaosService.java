package com.sentinel.api.service;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChaosService {

    private final KafkaTemplate<String, LogEventDTO> kafkaTemplate;
    private final SimulationService simulationService;

    private static final String TOPIC = "log-events";
    private static final String POD_SUFFIX = "-pod-";
    private static final String ANOMALY_KEY = "anomaly";

    private final Random random = new Random();

    public enum AnomalyScenario {
        ERROR_SPIKE,
        LATENCY_SURGE,
        DB_OUTAGE,
        MEMORY_LEAK,
        DOWNSTREAM_FAILURE,
        RATE_LIMIT_SPIKE,
        CONFIG_ERROR
    }

    private static final List<String> SERVICES = List.of(
            "payment-service", "order-service", "inventory-service",
            "notification-service", "user-service"
    );

    public List<String> getAvailableServices() {
        return SERVICES;
    }

    public List<Map<String, String>> getAvailableScenarios() {
        return List.of(
                Map.of("id", "ERROR_SPIKE", "label", "Error Spike", "description", "Sudden burst of ERROR logs", "icon", "🚨"),
                Map.of("id", "LATENCY_SURGE", "label", "Latency Surge", "description", "API latency spikes dramatically", "icon", "⏳"),
                Map.of("id", "DB_OUTAGE", "label", "DB Outage", "description", "Database connection failures", "icon", "🗄️"),
                Map.of("id", "MEMORY_LEAK", "label", "Memory Leak", "description", "Gradual latency increase + OOM errors", "icon", "💧"),
                Map.of("id", "DOWNSTREAM_FAILURE", "label", "Downstream Failure", "description", "External service timeout", "icon", "🔌"),
                Map.of("id", "RATE_LIMIT_SPIKE", "label", "Rate Limit Spike", "description", "Many 429 rate limit errors", "icon", "🛑"),
                Map.of("id", "CONFIG_ERROR", "label", "Config Error", "description", "Configuration validation failures", "icon", "⚙️")
        );
    }

    public List<LogEventDTO> injectAnomaly(String scenarioStr, String serviceName) {
        if (!simulationService.isSimulationActive()) {
            throw new IllegalStateException("Simulation is not active. Cannot inject chaos.");
        }

        AnomalyScenario scenario = AnomalyScenario.valueOf(scenarioStr);
        log.warn("🚨 INJECTING ANOMALY: {} on service: {}", scenario, serviceName);

        List<LogEventDTO> events = switch (scenario) {
            case ERROR_SPIKE -> generateErrorSpike(serviceName);
            case LATENCY_SURGE -> generateLatencySurge(serviceName);
            case DB_OUTAGE -> generateDbOutage(serviceName);
            case MEMORY_LEAK -> generateMemoryLeak(serviceName);
            case DOWNSTREAM_FAILURE -> generateDownstreamFailure(serviceName);
            case RATE_LIMIT_SPIKE -> generateRateLimitSpike(serviceName);
            case CONFIG_ERROR -> generateConfigError(serviceName);
        };

        for (LogEventDTO event : events) {
            kafkaTemplate.send(TOPIC, serviceName, event);
        }
        
        log.info("🚨 Injected {} anomaly events for scenario {}", events.size(), scenario);
        return events;
    }

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

    private List<LogEventDTO> generateDbOutage(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();

        String dbErrorMessage = "FATAL: Cannot acquire database connection from pool - pool exhausted";
        String dbStackTrace = "org.springframework.dao.DataAccessResourceFailureException: Unable to acquire JDBC Connection\n" +
                "\tat org.hibernate.engine.jdbc.connections.internal.ConnectionProviderInitiator.initiateService\n" +
                "\tat com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:213)\n" +
                "\tat com.sentinel.repository.OrderRepository.findById(OrderRepository.java:45)";

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

    private List<LogEventDTO> generateDownstreamFailure(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();

        for (int i = 0; i < 45; i++) {
            events.add(LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now(ZoneId.systemDefault()).plusNanos(i * 200_000_000L))
                    .serviceName(serviceName)
                    .logLevel(LogLevel.ERROR)
                    .message(String.format("Downstream service timeout after %dms", 5000 + random.nextInt(3000)))
                    .stackTrace("java.net.SocketTimeoutException: Read timed out\n" +
                            "\tat sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:278)")
                    .latencyMs(5000 + random.nextInt(3000))
                    .statusCode(504)
                    .host(serviceName + POD_SUFFIX + random.nextInt(3))
                    .metadata(Map.of(ANOMALY_KEY, "downstream_failure", "env", "prod"))
                    .build());
        }
        return events;
    }

    private List<LogEventDTO> generateRateLimitSpike(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            events.add(LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now(ZoneId.systemDefault()).plusNanos(i * 150_000_000L))
                    .serviceName(serviceName)
                    .logLevel(LogLevel.WARN)
                    .message("Rate limit exceeded for external API gateway — retry-after: 60s — request-id: " + UUID.randomUUID().toString().substring(0, 8))
                    .latencyMs(10 + random.nextInt(50))
                    .statusCode(429)
                    .host(serviceName + POD_SUFFIX + random.nextInt(3))
                    .metadata(Map.of(ANOMALY_KEY, "rate_limit_spike", "env", "prod"))
                    .build());
        }
        return events;
    }

    private List<LogEventDTO> generateConfigError(String serviceName) {
        List<LogEventDTO> events = new ArrayList<>();

        for (int i = 0; i < 35; i++) {
            events.add(LogEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now(ZoneId.systemDefault()).plusNanos(i * 100_000_000L))
                    .serviceName(serviceName)
                    .logLevel(LogLevel.ERROR)
                    .message("Configuration validation failed: invalid JWT signing key after deployment v2.4.1")
                    .latencyMs(5 + random.nextInt(20))
                    .statusCode(i % 2 == 0 ? 401 : 403)
                    .host(serviceName + POD_SUFFIX + random.nextInt(3))
                    .metadata(Map.of(ANOMALY_KEY, "config_error", "env", "prod"))
                    .build());
        }
        return events;
    }
}
