package com.sentinel.api.service;

import com.sentinel.api.dto.DashboardStatsDTO;
import com.sentinel.api.dto.IncidentDTO;
import com.sentinel.api.dto.ManualDispositionRequest;
import com.sentinel.api.entity.Incident;
import com.sentinel.api.repository.AnomalyRecordRepository;
import com.sentinel.api.repository.IncidentRepository;
import com.sentinel.api.repository.LogEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * IncidentService — business logic for incident management.
 * Handles CRUD operations, status transitions, manual disposition,
 * and dashboard statistics aggregation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AnomalyRecordRepository anomalyRecordRepository;
    private final LogEventRepository logEventRepository;

    /**
     * Get paginated incidents with optional filtering.
     */
    public Page<IncidentDTO> getIncidents(int page, int size, String severity, String status, String service) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Incident> incidents;
        if (severity != null && !severity.isBlank()) {
            incidents = incidentRepository.findBySeverityOrderByDetectedAtDesc(severity, pageable);
        } else if (status != null && !status.isBlank()) {
            incidents = incidentRepository.findByStatusOrderByDetectedAtDesc(status, pageable);
        } else if (service != null && !service.isBlank()) {
            incidents = incidentRepository.findByServiceNameOrderByDetectedAtDesc(service, pageable);
        } else {
            incidents = incidentRepository.findAllByOrderByDetectedAtDesc(pageable);
        }

        return incidents.map(this::toDTO);
    }

    /**
     * Get a single incident by its UUID.
     */
    public IncidentDTO getIncident(UUID incidentId) {
        Incident incident = incidentRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + incidentId));
        return toDTO(incident);
    }

    /**
     * Get all UNKNOWN incidents for the manual triage queue.
     */
    public List<IncidentDTO> getUnknownIncidents() {
        return incidentRepository.findUnknownIncidents()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Mark an incident as RESOLVED and calculate MTTR.
     */
    @Transactional
    public IncidentDTO resolveIncident(UUID incidentId) {
        Incident incident = incidentRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + incidentId));

        incident.setStatus("RESOLVED");
        incident.setResolvedAt(LocalDateTime.now());

        // Calculate MTTR (Mean Time To Resolve)
        if (incident.getDetectedAt() != null) {
            long seconds = Duration.between(incident.getDetectedAt(), incident.getResolvedAt()).getSeconds();
            incident.setMttrSeconds((int) seconds);
        }

        incidentRepository.save(incident);
        log.info("✅ Incident {} resolved. MTTR={}s", incidentId, incident.getMttrSeconds());
        return toDTO(incident);
    }

    /**
     * Mark an incident as FALSE_POSITIVE (dismissed).
     */
    @Transactional
    public IncidentDTO dismissIncident(UUID incidentId) {
        Incident incident = incidentRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + incidentId));

        incident.setStatus("FALSE_POSITIVE");
        incident.setResolvedAt(LocalDateTime.now());
        incidentRepository.save(incident);

        log.info("🚫 Incident {} dismissed as false positive", incidentId);
        return toDTO(incident);
    }

    /**
     * Manual disposition — engineer manually fills in the RCA for UNKNOWN incidents.
     */
    @Transactional
    public IncidentDTO manualDisposition(UUID incidentId, ManualDispositionRequest request) {
        Incident incident = incidentRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + incidentId));

        incident.setRootCause(request.getRootCause());
        incident.setRcaSummary(request.getRcaSummary());
        incident.setImpactAnalysis(request.getImpactAnalysis());
        incident.setSuggestedFix(request.getSuggestedFix());
        incident.setPrevention(request.getPrevention());
        incident.setConfidence(1.0); // Manual = 100% confidence
        incident.setAnalyzedAt(LocalDateTime.now());
        incident.setStatus("OPEN"); // Now it has a real RCA, back to OPEN for resolution

        incidentRepository.save(incident);
        log.info("✍️ Manual disposition applied to incident {}", incidentId);
        return toDTO(incident);
    }

    /**
     * Get dashboard summary statistics.
     */
    public DashboardStatsDTO getDashboardStats(boolean simulationActive) {
        return DashboardStatsDTO.builder()
                .totalIncidents(incidentRepository.count())
                .openIncidents(incidentRepository.countByStatus("OPEN"))
                .resolvedIncidents(incidentRepository.countByStatus("RESOLVED"))
                .unknownIncidents(incidentRepository.findUnknownIncidents().size())
                .totalAnomalies(anomalyRecordRepository.count())
                .totalLogEvents(logEventRepository.count())
                .p0Count(incidentRepository.countBySeverity("P0"))
                .p1Count(incidentRepository.countBySeverity("P1"))
                .p2Count(incidentRepository.countBySeverity("P2"))
                .p3Count(incidentRepository.countBySeverity("P3"))
                .averageMttrSeconds(incidentRepository.averageMttr())
                .simulationActive(simulationActive)
                .build();
    }

    // ─── Mapper ──────────────────────────────────────────────────────────────

    private IncidentDTO toDTO(Incident entity) {
        return IncidentDTO.builder()
                .incidentId(entity.getIncidentId())
                .anomalyId(entity.getAnomalyId())
                .title(entity.getTitle())
                .severity(entity.getSeverity())
                .status(entity.getStatus())
                .serviceName(entity.getServiceName())
                .rcaSummary(entity.getRcaSummary())
                .rootCause(entity.getRootCause())
                .impactAnalysis(entity.getImpactAnalysis())
                .suggestedFix(entity.getSuggestedFix())
                .prevention(entity.getPrevention())
                .confidence(entity.getConfidence())
                .detectedAt(entity.getDetectedAt())
                .analyzedAt(entity.getAnalyzedAt())
                .resolvedAt(entity.getResolvedAt())
                .mttrSeconds(entity.getMttrSeconds())
                .relatedLogs(entity.getRelatedLogs())
                .similarIncidents(entity.getSimilarIncidents())
                .build();
    }
}
