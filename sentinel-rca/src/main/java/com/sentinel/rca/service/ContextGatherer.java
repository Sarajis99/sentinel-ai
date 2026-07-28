package com.sentinel.rca.service;

import com.sentinel.common.dto.AnomalyDTO;
import com.sentinel.rca.entity.LogEvent;
import com.sentinel.rca.repository.LogEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ContextGatherer — fetches raw log events from PostgreSQL around an anomaly.
 *
 * Queries ±windowMinutes of logs for the affected service and formats them
 * into a human-readable text block for the LLM prompt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextGatherer {

    private final LogEventRepository logEventRepository;

    @Value("${rca.context.window-minutes:5}")
    private int windowMinutes;

    @Value("${rca.context.max-log-entries:50}")
    private int maxLogEntries;

    /**
     * Fetches relevant logs for an anomaly and formats them for the LLM.
     *
     * @param anomaly The anomaly to gather context for
     * @return Formatted log context string to embed in the prompt
     */
    public String gatherContext(AnomalyDTO anomaly) {
        LocalDateTime from = anomaly.getDetectedAt().minusMinutes(windowMinutes);
        LocalDateTime to = anomaly.getDetectedAt().plusMinutes(1); // +1 min after detection

        log.info("📋 Gathering log context for service={} window=[{} → {}]",
                anomaly.getServiceName(), from, to);

        // Fetch critical (ERROR + WARN) logs first
        List<LogEvent> criticalLogs = logEventRepository.findCriticalLogsByServiceAndWindow(
                anomaly.getServiceName(), from, to
        );

        List<LogEvent> logsToUse = criticalLogs.size() >= 5
                ? criticalLogs
                : logEventRepository.findByServiceNameAndTimestampBetween(anomaly.getServiceName(), from, to);

        // Cap at max entries to stay within LLM token limits
        if (logsToUse.size() > maxLogEntries) {
            logsToUse = logsToUse.subList(0, maxLogEntries);
        }

        log.info("📋 Found {} relevant log entries for context", logsToUse.size());

        return formatLogsForPrompt(logsToUse);
    }

    /**
     * Formats a list of log events into a readable text block for the LLM prompt.
     */
    private String formatLogsForPrompt(List<LogEvent> logs) {
        if (logs.isEmpty()) {
            return "No logs found in the detection window.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== RAW LOG ENTRIES (").append(logs.size()).append(" entries) ===\n\n");

        for (LogEvent log : logs) {
            sb.append("[").append(log.getTimestamp()).append("] ")
              .append("[").append(log.getLogLevel()).append("] ")
              .append(log.getServiceName()).append(" — ")
              .append(log.getMessage());

            if (log.getLatencyMs() != null) {
                sb.append(" | latency=").append(log.getLatencyMs()).append("ms");
            }
            if (log.getStatusCode() != null) {
                sb.append(" | status=").append(log.getStatusCode());
            }
            if (log.getStackTrace() != null && !log.getStackTrace().isBlank()) {
                // Include first 3 lines of stack trace only (token budget)
                String[] stackLines = log.getStackTrace().split("\n");
                int linesToInclude = Math.min(stackLines.length, 3);
                sb.append("\n  Stack: ");
                for (int i = 0; i < linesToInclude; i++) {
                    sb.append(stackLines[i].trim()).append(" | ");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
