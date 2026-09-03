package com.sentinel.api.service;

import com.sentinel.api.dto.DashboardStatsDTO;
import com.sentinel.api.dto.IncidentDTO;
import com.sentinel.api.dto.IncidentCommentDTO;
import com.sentinel.api.dto.ManualDispositionRequest;
import com.sentinel.api.entity.Incident;
import com.sentinel.api.entity.IncidentComment;
import com.sentinel.api.repository.AnomalyRecordRepository;
import com.sentinel.api.repository.IncidentCommentRepository;
import com.sentinel.api.repository.IncidentRepository;
import com.sentinel.api.repository.LogEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private AnomalyRecordRepository anomalyRecordRepository;
    @Mock
    private LogEventRepository logEventRepository;
    @Mock
    private IncidentCommentRepository incidentCommentRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private IncidentService incidentService;

    private Incident incident;
    private UUID incidentId;

    @BeforeEach
    void setUp() {
        incidentId = UUID.randomUUID();
        incident = Incident.builder()
                .incidentId(incidentId)
                .status("IN_PROGRESS")
                .detectedAt(LocalDateTime.now().minusMinutes(30))
                .build();
    }

    @Test
    void getIncidents() {
        when(incidentRepository.findAllByOrderByDetectedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(incident)));
        
        Page<IncidentDTO> result = incidentService.getIncidents(0, 10, null, null, null);
        
        assertEquals(1, result.getContent().size());
        assertEquals(incidentId, result.getContent().get(0).getIncidentId());
    }

    @Test
    void getIncident() {
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        IncidentDTO result = incidentService.getIncident(incidentId);
        
        assertEquals(incidentId, result.getIncidentId());
    }

    @Test
    void getIncident_NotFound() {
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.empty());
        
        assertThrows(RuntimeException.class, () -> incidentService.getIncident(incidentId));
    }

    @Test
    void resolveIncident() {
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        IncidentDTO result = incidentService.resolveIncident(incidentId);
        
        assertEquals("RESOLVED", result.getStatus());
        assertNotNull(result.getResolvedAt());
        assertTrue(result.getMttrSeconds() > 0);
        verify(incidentRepository).save(incident);
    }

    @Test
    void resolveIncident_WrongStatus() {
        incident.setStatus("CLOSED");
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        assertThrows(IllegalStateException.class, () -> incidentService.resolveIncident(incidentId));
    }

    @Test
    void dismissIncident() {
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        IncidentDTO result = incidentService.dismissIncident(incidentId);
        
        assertEquals("CLOSED", result.getStatus());
        assertNotNull(result.getResolvedAt());
        verify(incidentRepository).save(incident);
    }

    @Test
    void manualDisposition_AwaitingTriage_SingleTriage() {
        incident.setStatus("AWAITING_TRIAGE");
        incident.setConfidence(null);
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        ManualDispositionRequest request = new ManualDispositionRequest();
        request.setRootCause("Test Root Cause");
        request.setRcaSummary("Test Summary");
        
        IncidentDTO result = incidentService.manualDisposition(incidentId, request);
        
        assertEquals("RCA_COMPLETE", result.getStatus());
        assertEquals("Test Root Cause", result.getRootCause());
        assertEquals("Test Summary", result.getRcaSummary());
        assertNull(result.getConfidence());
        assertNull(result.getManualRootCause());
        verify(incidentRepository).save(incident);
    }

    @Test
    void manualDisposition_WithExistingAi_DualTriage() {
        incident.setStatus("RCA_COMPLETE");
        incident.setRootCause("AI Root Cause");
        incident.setRcaSummary("AI Summary");
        incident.setConfidence(0.88);
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        ManualDispositionRequest request = new ManualDispositionRequest();
        request.setRootCause("Human Expert Root Cause");
        request.setRcaSummary("Human Expert Summary");
        
        IncidentDTO result = incidentService.manualDisposition(incidentId, request);
        
        assertEquals("RCA_COMPLETE", result.getStatus());
        assertEquals("AI Root Cause", result.getRootCause());
        assertEquals("AI Summary", result.getRcaSummary());
        assertEquals(0.88, result.getConfidence());
        assertEquals("Human Expert Root Cause", result.getManualRootCause());
        assertEquals("Human Expert Summary", result.getManualRcaSummary());
        assertNotNull(result.getManualTriagedAt());
        verify(incidentRepository).save(incident);
    }

    @Test
    void acceptIncident() {
        incident.setStatus("RCA_COMPLETE");
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        IncidentDTO result = incidentService.acceptIncident(incidentId);
        
        assertEquals("IN_PROGRESS", result.getStatus());
        verify(incidentRepository).save(incident);
    }

    @Test
    void closeIncident() {
        incident.setStatus("RESOLVED");
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        IncidentDTO result = incidentService.closeIncident(incidentId);
        
        assertEquals("CLOSED", result.getStatus());
        verify(incidentRepository).save(incident);
    }

    @Test
    void retryAnalysis() {
        incident.setStatus("AWAITING_TRIAGE");
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        incidentService.retryAnalysis(incidentId);
        
        verify(kafkaTemplate).send("rca-retry-events", incidentId.toString(), incidentId.toString());
    }

    @Test
    void getComments() {
        IncidentComment comment = IncidentComment.builder().incidentId(incidentId).content("Hello").build();
        when(incidentCommentRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId))
                .thenReturn(List.of(comment));
        
        List<IncidentCommentDTO> result = incidentService.getComments(incidentId);
        
        assertEquals(1, result.size());
        assertEquals("Hello", result.get(0).getContent());
    }

    @Test
    void addComment() {
        when(incidentRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
        
        IncidentCommentDTO result = incidentService.addComment(incidentId, "User", "Test");
        
        assertEquals("User", result.getAuthor());
        assertEquals("Test", result.getContent());
        verify(incidentCommentRepository).save(any(IncidentComment.class));
    }

    @Test
    void getDashboardStats() {
        when(incidentRepository.count()).thenReturn(10L);
        
        DashboardStatsDTO result = incidentService.getDashboardStats(true);
        
        assertEquals(10L, result.getTotalIncidents());
        assertTrue(result.isSimulationActive());
    }
}
