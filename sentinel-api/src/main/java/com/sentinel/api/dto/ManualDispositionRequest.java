package com.sentinel.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for manual disposition — engineer manually fills in the RCA.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualDispositionRequest {
    private String rootCause;
    private String rcaSummary;
    private String impactAnalysis;
    private String suggestedFix;
    private String prevention;
}
