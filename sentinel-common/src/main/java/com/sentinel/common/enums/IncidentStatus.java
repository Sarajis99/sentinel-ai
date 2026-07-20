package com.sentinel.common.enums;

public enum IncidentStatus {
    OPEN,           // Anomaly detected, not yet analyzed
    ANALYZING,      // LLM generating RCA
    RESOLVED,       // Manually marked resolved
    FALSE_POSITIVE  // Marked as not a real issue
}
