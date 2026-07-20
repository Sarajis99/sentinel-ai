package com.sentinel.common.enums;

/**
 * Incident severity levels — P0 is most critical
 */
public enum Severity {
    P0,   // Critical — immediate action required, service down
    P1,   // High — major degradation, affecting users
    P2,   // Medium — partial degradation, some users affected
    P3    // Low — minor issue, monitoring needed
}
