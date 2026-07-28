package com.sentinel.rca.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured RCA response parsed from the LLM output.
 * The LLM is instructed to respond in JSON with these exact fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RCAResponse {

    /**
     * Root cause category — maps the mathematical symptom to the actual cause.
     * Examples: "DB_OUTAGE", "MEMORY_LEAK", "DOWNSTREAM_FAILURE", "DEPLOYMENT_ISSUE", "TRAFFIC_SPIKE"
     */
    private String rootCause;

    /**
     * Short one-liner title for the incident dashboard.
     * Example: "Payment Service DB Connection Pool Exhausted"
     */
    private String title;

    /**
     * 2-3 sentence plain English summary of what happened.
     */
    private String rcaSummary;

    /**
     * Detailed explanation of the root cause with evidence from logs.
     */
    private String rootCauseDetail;

    /**
     * What is the blast radius — which users/services are affected?
     */
    private String impactAnalysis;

    /**
     * Concrete step-by-step fix for the on-call engineer.
     */
    private String suggestedFix;

    /**
     * How to prevent this from happening again.
     */
    private String prevention;

    /**
     * LLM confidence in its analysis: 0.0 (not sure) to 1.0 (very sure).
     */
    @Builder.Default
    private Double confidence = 0.5;

    /**
     * Was this parsed successfully from LLM output?
     */
    @Builder.Default
    private boolean parseSuccess = true;
}
