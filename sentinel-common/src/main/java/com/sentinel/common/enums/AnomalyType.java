package com.sentinel.common.enums;

public enum AnomalyType {
    ERROR_SPIKE,          // Sudden surge in ERROR log rate
    LATENCY_SURGE,        // API response time spikes above threshold
    AVAILABILITY_DROP,    // Service returning 5xx / timing out
    THROUGHPUT_DROP       // Requests per second drops significantly
}
