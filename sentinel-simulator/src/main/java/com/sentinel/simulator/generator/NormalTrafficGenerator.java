package com.sentinel.simulator.generator;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.common.enums.LogLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generates realistic log events simulating a microservices environment.
 * Simulates 5 services with realistic traffic patterns.
 */
@Slf4j
@Component
public class NormalTrafficGenerator {

    private final Random random = new Random();

    // Simulated microservices
    private static final List<String> SERVICES = List.of(
            "payment-service",
            "order-service",
            "inventory-service",
            "notification-service",
            "user-service"
    );

    // Normal INFO messages per service
    private static final Map<String, List<String>> INFO_MESSAGES = Map.of(
            "payment-service", List.of(
                    "Payment processed successfully for orderId={}",
                    "Transaction validated for customerId={}",
                    "Payment gateway responded in {}ms",
                    "Refund initiated for transactionId={}"
            ),
            "order-service", List.of(
                    "Order created successfully: orderId={}",
                    "Order status updated to CONFIRMED for orderId={}",
                    "Inventory reserved for orderId={}",
                    "Order dispatched: orderId={}"
            ),
            "inventory-service", List.of(
                    "Stock level checked for productId={}",
                    "Inventory updated: productId={} quantity={}",
                    "Low stock alert triggered for productId={}",
                    "Restock request sent for productId={}"
            ),
            "notification-service", List.of(
                    "Email sent to userId={}",
                    "SMS notification delivered to userId={}",
                    "Push notification queued for userId={}",
                    "Notification template rendered in {}ms"
            ),
            "user-service", List.of(
                    "User login successful: userId={}",
                    "Profile updated for userId={}",
                    "Password reset requested for userId={}",
                    "Session created for userId={}"
            )
    );

    // Realistic WARN messages
    private static final List<String> WARN_MESSAGES = List.of(
            "Slow database query detected: {}ms for query={}",
            "Retry attempt {} for external API call",
            "Cache miss rate elevated: {}%",
            "Connection pool utilization at {}%",
            "Rate limit approaching for clientId={}"
    );

    // Realistic ERROR messages with stack traces
    private static final List<String> ERROR_MESSAGES = List.of(
            "Database connection timeout after {}ms",
            "NullPointerException in OrderProcessor.process()",
            "HTTP 503 from downstream service after {} retries",
            "Failed to deserialize message from Kafka topic",
            "Redis connection refused: max retry exceeded"
    );

    private static final List<String> ERROR_STACK_TRACES = List.of(
            "java.sql.SQLTimeoutException: Timeout waiting for connection from pool\n" +
            "\tat com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:213)\n" +
            "\tat com.sentinel.service.DatabaseService.query(DatabaseService.java:87)",

            "java.lang.NullPointerException: Cannot invoke method on null object\n" +
            "\tat com.sentinel.service.OrderProcessor.process(OrderProcessor.java:142)\n" +
            "\tat com.sentinel.controller.OrderController.createOrder(OrderController.java:67)",

            "org.springframework.web.client.ResourceAccessException: I/O error on POST request\n" +
            "\tat org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:748)\n" +
            "\tat com.sentinel.client.PaymentGatewayClient.charge(PaymentGatewayClient.java:93)"
    );

    /**
     * Generate a realistic normal log event for a random service
     */
    public LogEventDTO generateNormalEvent() {
        String service = SERVICES.get(random.nextInt(SERVICES.size()));
        LogLevel level = pickNormalLogLevel();

        return LogEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .serviceName(service)
                .logLevel(level)
                .message(generateMessage(service, level))
                .stackTrace(level == LogLevel.ERROR ? pickRandom(ERROR_STACK_TRACES) : null)
                .requestId(UUID.randomUUID().toString().substring(0, 8))
                .latencyMs(generateNormalLatency(service))
                .statusCode(generateNormalStatusCode(level))
                .host(service + "-pod-" + random.nextInt(3))
                .metadata(Map.of("env", "prod", "region", "ap-south-1"))
                .build();
    }

    /**
     * Normal distribution of log levels:
     * 75% INFO, 18% WARN, 5% ERROR, 2% DEBUG
     */
    private LogLevel pickNormalLogLevel() {
        int roll = random.nextInt(100);
        if (roll < 75) return LogLevel.INFO;
        if (roll < 93) return LogLevel.WARN;
        if (roll < 98) return LogLevel.ERROR;
        return LogLevel.DEBUG;
    }

    private String generateMessage(String service, LogLevel level) {
        return switch (level) {
            case INFO -> {
                List<String> msgs = INFO_MESSAGES.getOrDefault(service,
                        List.of("Operation completed for requestId={}"));
                yield pickRandom(msgs)
                        .replace("{}", String.valueOf(random.nextInt(100000)));
            }
            case WARN -> pickRandom(WARN_MESSAGES)
                    .replace("{}", String.valueOf(random.nextInt(100)));
            case ERROR -> pickRandom(ERROR_MESSAGES)
                    .replace("{}", String.valueOf(random.nextInt(10)));
            case DEBUG -> "Debug trace for requestId=" + UUID.randomUUID().toString().substring(0, 8);
        };
    }

    private int generateNormalLatency(String service) {
        // Different services have different baseline latencies
        int baseLatency = switch (service) {
            case "payment-service" -> 150;
            case "order-service" -> 80;
            case "inventory-service" -> 50;
            case "notification-service" -> 200;
            default -> 100;
        };
        // Add ±30% jitter
        int jitter = (int) (baseLatency * 0.3);
        return baseLatency + random.nextInt(jitter * 2) - jitter;
    }

    private int generateNormalStatusCode(LogLevel level) {
        if (level == LogLevel.ERROR) {
            int[] errorCodes = {500, 503, 502, 504};
            return errorCodes[random.nextInt(errorCodes.length)];
        }
        if (level == LogLevel.WARN) return random.nextBoolean() ? 200 : 429;
        return 200;
    }

    private <T> T pickRandom(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    public List<String> getServices() {
        return SERVICES;
    }
}
