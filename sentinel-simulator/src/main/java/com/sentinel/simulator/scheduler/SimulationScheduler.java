package com.sentinel.simulator.scheduler;

import com.sentinel.common.dto.LogEventDTO;
import com.sentinel.simulator.generator.AnomalyInjector;
import com.sentinel.simulator.generator.NormalTrafficGenerator;
import com.sentinel.simulator.producer.LogEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * Orchestrates the simulation:
 * - Every 200ms: Send 1 normal log event
 * - Every 2 minutes: Inject an anomaly on a random service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationScheduler {

    private final NormalTrafficGenerator normalGenerator;
    private final AnomalyInjector anomalyInjector;
    private final LogEventProducer producer;
    private final Random random = new Random();

    private long totalEventsSent = 0;

    /**
     * Send normal traffic every 200ms (~5 events/second, ~300/minute)
     */
    @Scheduled(fixedDelay = 200)
    public void sendNormalTraffic() {
        LogEventDTO event = normalGenerator.generateNormalEvent();
        producer.send(event);
        totalEventsSent++;

        if (totalEventsSent % 100 == 0) {
            log.info("📊 Simulator stats: {} total events sent", totalEventsSent);
        }
    }

    /**
     * Inject an anomaly every 2 minutes on a random service
     * This gives the detection engine enough baseline data first
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void injectAnomaly() {
        List<String> services = normalGenerator.getServices();
        String targetService = services.get(random.nextInt(services.size()));
        AnomalyInjector.AnomalyScenario scenario = anomalyInjector.pickRandomScenario();

        log.warn("🚨 === ANOMALY INJECTION START === service={} scenario={}", targetService, scenario);

        List<LogEventDTO> anomalyEvents = anomalyInjector.injectAnomaly(targetService, scenario);
        anomalyEvents.forEach(producer::send);

        log.warn("🚨 === ANOMALY INJECTION END === sent {} anomaly events", anomalyEvents.size());
    }
}
