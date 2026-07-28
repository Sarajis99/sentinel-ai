package com.sentinel.rca.service;

import com.sentinel.common.dto.AnomalyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PromptBuilder — constructs the full LLM prompt for RCA generation.
 *
 * Combines the anomaly signal metadata with the raw log context into
 * a structured prompt that yields reliable, parseable JSON output.
 */
@Slf4j
@Component
public class PromptBuilder {

    /**
     * Builds the complete user-facing prompt sent to the LLM.
     *
     * @param anomaly      The anomaly signal from the Detector
     * @param logContext   Formatted log entries from ContextGatherer
     * @return Complete prompt string ready to send to the LLM
     */
    public String build(AnomalyDTO anomaly, String logContext) {
        return """
                ## ANOMALY DETECTION ALERT
                
                **Service:** %s
                **Detected At:** %s
                **Anomaly Type:** %s (mathematical symptom detected by statistical analysis)
                **Severity:** %s
                **Metric:** %s
                **Expected Value:** %.4f
                **Actual Value:** %.4f
                **Deviation:** %.1f%% above expected
                **Detection Window:** %d minutes
                
                ---
                
                ## RAW LOG CONTEXT
                
                The following logs were collected from '%s' in the ±5 minutes around the anomaly:
                
                %s
                
                ---
                
                ## YOUR TASK
                
                Based on the anomaly metrics and the raw log evidence above:
                1. Identify the TRUE root cause (not just "error spike" — what CAUSED the errors?)
                2. Look for patterns: database errors, timeout messages, OOM errors, downstream failures, etc.
                3. Assess user impact
                4. Provide concrete remediation steps
                
                Respond ONLY with the JSON object. No markdown, no preamble.
                """.formatted(
                anomaly.getServiceName(),
                anomaly.getDetectedAt(),
                anomaly.getAnomalyType(),
                anomaly.getSeverity(),
                anomaly.getMetricName(),
                safeDouble(anomaly.getExpectedValue()),
                safeDouble(anomaly.getActualValue()),
                calculateDeviation(anomaly.getExpectedValue(), anomaly.getActualValue()),
                anomaly.getWindowMinutes() != null ? anomaly.getWindowMinutes() : 5,
                anomaly.getServiceName(),
                logContext
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private double calculateDeviation(Double expected, Double actual) {
        if (expected == null || expected == 0 || actual == null) return 0.0;
        return ((actual - expected) / expected) * 100.0;
    }
}
