package com.sentinel.api.controller;

import com.sentinel.api.dto.CommentRequest;
import com.sentinel.api.dto.DashboardStatsDTO;
import com.sentinel.api.dto.IncidentCommentDTO;
import com.sentinel.api.dto.IncidentDTO;
import com.sentinel.api.dto.ManualDispositionRequest;
import com.sentinel.api.security.RateLimiterService;
import com.sentinel.api.service.IncidentService;
import com.sentinel.api.service.SimulationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IncidentController — REST API for incident management.
 *
 * Endpoints:
 *   GET  /api/v1/incidents           — Paginated, filterable incident list
 *   GET  /api/v1/incidents/{id}      — Single incident detail
 *   GET  /api/v1/incidents/unknown   — Manual triage queue (UNKNOWN root cause)
 *   GET  /api/v1/incidents/stats     — Dashboard summary statistics
 *   POST /api/v1/incidents/{id}/resolve      — Mark as resolved
 *   POST /api/v1/incidents/{id}/dismiss      — Mark as false positive
 *   PUT  /api/v1/incidents/{id}/manual-disposition — Engineer fills RCA manually
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final SimulationService simulationService;
    private final RateLimiterService rateLimiterService;

    /**
     * GET /api/v1/incidents?page=0&size=20&severity=P0&status=OPEN&service=payment-service
     * Returns paginated incidents with optional filtering.
     */
    @GetMapping
    public ResponseEntity<Page<IncidentDTO>> getIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String service) {

        log.debug("GET /incidents page={} size={} severity={} status={} service={}",
                page, size, severity, status, service);
        return ResponseEntity.ok(incidentService.getIncidents(page, size, severity, status, service));
    }

    /**
     * GET /api/v1/incidents/{id}
     * Returns a single incident with full RCA details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<IncidentDTO> getIncident(@PathVariable("id") UUID incidentId) {
        return ResponseEntity.ok(incidentService.getIncident(incidentId));
    }

    /**
     * GET /api/v1/incidents/unknown
     * Returns all UNKNOWN incidents for the manual triage queue.
     */
    @GetMapping("/unknown")
    public ResponseEntity<List<IncidentDTO>> getUnknownIncidents() {
        return ResponseEntity.ok(incidentService.getUnknownIncidents());
    }

    /**
     * GET /api/v1/incidents/stats
     * Returns dashboard summary statistics (total incidents, MTTR, severity breakdown).
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        boolean simActive = simulationService.isSimulationActive();
        return ResponseEntity.ok(incidentService.getDashboardStats(simActive));
    }

    /**
     * POST /api/v1/incidents/{id}/resolve
     * Marks an incident as RESOLVED and calculates MTTR.
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<IncidentDTO> resolveIncident(@PathVariable("id") UUID incidentId) {
        log.info("POST /incidents/{}/resolve", incidentId);
        return ResponseEntity.ok(incidentService.resolveIncident(incidentId));
    }

    /**
     * POST /api/v1/incidents/{id}/dismiss
     * Marks an incident as FALSE_POSITIVE.
     */
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<IncidentDTO> dismissIncident(@PathVariable("id") UUID incidentId) {
        log.info("POST /incidents/{}/dismiss", incidentId);
        return ResponseEntity.ok(incidentService.dismissIncident(incidentId));
    }

    /**
     * PUT /api/v1/incidents/{id}/manual-disposition
     * Engineer manually fills in the RCA for UNKNOWN incidents.
     */
    @PutMapping("/{id}/manual-disposition")
    public ResponseEntity<IncidentDTO> manualDisposition(
            @PathVariable("id") UUID incidentId,
            @RequestBody ManualDispositionRequest request) {

        log.info("PUT /incidents/{}/manual-disposition rootCause={}", incidentId, request.getRootCause());
        return ResponseEntity.ok(incidentService.manualDisposition(incidentId, request));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<IncidentDTO> acceptIncident(@PathVariable("id") UUID incidentId) {
        log.info("POST /incidents/{}/accept", incidentId);
        return ResponseEntity.ok(incidentService.acceptIncident(incidentId));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<IncidentDTO> closeIncident(@PathVariable("id") UUID incidentId) {
        log.info("POST /incidents/{}/close", incidentId);
        return ResponseEntity.ok(incidentService.closeIncident(incidentId));
    }

    @PostMapping("/{id}/retry-analysis")
    public ResponseEntity<Map<String, String>> retryAnalysis(@PathVariable("id") UUID incidentId, HttpServletRequest request) {
        log.info("POST /incidents/{}/retry-analysis", incidentId);
        String clientIp = request.getRemoteAddr();
        if (!rateLimiterService.isAllowed("retry-analysis", clientIp, 5, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "status", "rate_limited",
                    "message", "Too many retry attempts. Please wait 1 minute before retrying."
            ));
        }
        incidentService.retryAnalysis(incidentId);
        return ResponseEntity.ok(Map.of("status", "retry_requested", "message", "AI analysis retry has been queued"));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<IncidentCommentDTO>> getComments(@PathVariable("id") UUID incidentId) {
        return ResponseEntity.ok(incidentService.getComments(incidentId));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<IncidentCommentDTO> addComment(@PathVariable("id") UUID incidentId, @RequestBody CommentRequest request) {
        log.info("POST /incidents/{}/comments", incidentId);
        return ResponseEntity.ok(incidentService.addComment(incidentId, request.getAuthor(), request.getContent()));
    }
}
