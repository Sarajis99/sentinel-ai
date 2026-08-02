package com.sentinel.api.service;

import com.sentinel.api.dto.DashboardStatsDTO;
import com.sentinel.api.dto.IncidentDTO;
import com.sentinel.api.dto.ManualDispositionRequest;
import com.sentinel.api.entity.Incident;
import com.sentinel.api.repository.AnomalyRecordRepository;
import com.sentinel.api.repository.IncidentRepository;
import com.sentinel.api.dto.IncidentCommentDTO;
import com.sentinel.api.entity.IncidentComment;
import com.sentinel.api.repository.IncidentCommentRepository;
import com.sentinel.api.repository.LogEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final IncidentCommentRepository incidentCommentRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

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

        if (!"IN_PROGRESS".equals(incident.getStatus())) {
            throw new IllegalStateException("Can only resolve incidents with status IN_PROGRESS. Current: " + incident.getStatus());
        }

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

        incident.setStatus("CLOSED");
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
        incident.setStatus("RCA_COMPLETE"); // Now it has a real RCA, back to RCA_COMPLETE for resolution

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
                .awaitingTriageCount(incidentRepository.countByStatus("AWAITING_TRIAGE"))
                .inProgressCount(incidentRepository.countByStatus("IN_PROGRESS"))
                .closedCount(incidentRepository.countByStatus("CLOSED"))
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

    /** Accept & begin work: RCA_COMPLETE → IN_PROGRESS */
    @Transactional
    public IncidentDTO acceptIncident(UUID incidentId) {
        Incident incident = findIncident(incidentId);
        if (!"RCA_COMPLETE".equals(incident.getStatus())) {
            throw new IllegalStateException("Can only accept incidents with status RCA_COMPLETE. Current: " + incident.getStatus());
        }
        incident.setStatus("IN_PROGRESS");
        incidentRepository.save(incident);
        log.info("🔧 Incident {} accepted — status → IN_PROGRESS", incidentId);
        return toDTO(incident);
    }

    /** Close incident: RESOLVED → CLOSED */
    @Transactional
    public IncidentDTO closeIncident(UUID incidentId) {
        Incident incident = findIncident(incidentId);
        if (!"RESOLVED".equals(incident.getStatus())) {
            throw new IllegalStateException("Can only close incidents with status RESOLVED. Current: " + incident.getStatus());
        }
        incident.setStatus("CLOSED");
        incidentRepository.save(incident);
        log.info("🔒 Incident {} closed", incidentId);
        return toDTO(incident);
    }

    /** Retry AI analysis — publish retry event to Kafka */
    public void retryAnalysis(UUID incidentId) {
        Incident incident = findIncident(incidentId);
        if (!"AWAITING_TRIAGE".equals(incident.getStatus())) {
            throw new IllegalStateException("Can only retry analysis for AWAITING_TRIAGE incidents. Current: " + incident.getStatus());
        }
        // Publish to Kafka topic 'rca-retry-events' with the incidentId as value
        kafkaTemplate.send("rca-retry-events", incidentId.toString(), incidentId.toString());
        log.info("🔄 Retry analysis requested for incident {}", incidentId);
    }

    /** Get comments for an incident */
    public List<IncidentCommentDTO> getComments(UUID incidentId) {
        return incidentCommentRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::toCommentDTO)
                .toList();
    }

    /** Add a comment to an incident */
    @Transactional
    public IncidentCommentDTO addComment(UUID incidentId, String author, String content) {
        // Verify incident exists
        findIncident(incidentId);
        
        IncidentComment comment = IncidentComment.builder()
                .commentId(UUID.randomUUID())
                .incidentId(incidentId)
                .author(author != null && !author.isBlank() ? author : "Analyst")
                .content(content)
                .build();
        incidentCommentRepository.save(comment);
        log.info("💬 Comment added to incident {} by {}", incidentId, comment.getAuthor());
        return toCommentDTO(comment);
    }

    private IncidentCommentDTO toCommentDTO(IncidentComment entity) {
        return IncidentCommentDTO.builder()
                .commentId(entity.getCommentId())
                .incidentId(entity.getIncidentId())
                .author(entity.getAuthor())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private Incident findIncident(UUID incidentId) {
        return incidentRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + incidentId));
    }

    // ─── Mapper ──────────────────────────────────────────────────────────────

    private IncidentDTO toDTO(Incident entity) {
        return IncidentDTO.builder()
                .incidentId(entity.getIncidentId())
                .incidentNumber(entity.getIncidentNumber())
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
