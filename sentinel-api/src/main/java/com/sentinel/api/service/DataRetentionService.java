package com.sentinel.api.service;

import com.sentinel.api.repository.AnomalyRecordRepository;
import com.sentinel.api.repository.IncidentRepository;
import com.sentinel.api.repository.LogEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DataRetentionService — the "Merged Strategy" cron job.
 *
 * Runs daily at midnight to enforce two safety mechanisms:
 * 1. Nightly Cleanup: Delete log_events and anomalies older than 24 hours
 * 2. Rolling Cap: Keep only the latest 1,000 incidents (delete oldest beyond cap)
 *
 * This guarantees the database never exceeds Neon.tech's 500MB free tier limit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final LogEventRepository logEventRepository;
    private final AnomalyRecordRepository anomalyRecordRepository;
    private final IncidentRepository incidentRepository;

    @Value("${retention.log-events-max-age-hours:24}")
    private int logEventsMaxAgeHours;

    @Value("${retention.anomalies-max-age-hours:24}")
    private int anomaliesMaxAgeHours;

    @Value("${retention.incidents-max-count:1000}")
    private int incidentsMaxCount;

    /**
     * Scheduled cleanup — runs at midnight daily.
     * Cron expression is configurable via application.yml.
     */
    @Scheduled(cron = "${retention.cron}")
    @Transactional
    public void performRetentionCleanup() {
        log.info("🧹 === DATA RETENTION CLEANUP START ===");

        // Step 1: Delete old log events
        LocalDateTime logCutoff = LocalDateTime.now().minusHours(logEventsMaxAgeHours);
        int deletedLogs = logEventRepository.deleteOlderThan(logCutoff);
        log.info("🗑️ Deleted {} log events older than {} hours", deletedLogs, logEventsMaxAgeHours);

        // Step 2: Delete old anomalies
        LocalDateTime anomalyCutoff = LocalDateTime.now().minusHours(anomaliesMaxAgeHours);
        int deletedAnomalies = anomalyRecordRepository.deleteOlderThan(anomalyCutoff);
        log.info("🗑️ Deleted {} anomalies older than {} hours", deletedAnomalies, anomaliesMaxAgeHours);

        // Step 3: Enforce the rolling 1000-incident cap
        List<Long> excessIds = incidentRepository.findIdsOlderThanOffset(incidentsMaxCount);
        if (!excessIds.isEmpty()) {
            int deletedIncidents = incidentRepository.deleteByIds(excessIds);
            log.info("🗑️ Deleted {} incidents beyond the {} cap", deletedIncidents, incidentsMaxCount);
        }

        log.info("🧹 === DATA RETENTION CLEANUP COMPLETE ===");
    }

    /**
     * Manual trigger for the retention cleanup (useful for testing).
     */
    public void triggerManualCleanup() {
        performRetentionCleanup();
    }
}
