package com.sentinel.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.api.dto.CommentRequest;
import com.sentinel.api.dto.DashboardStatsDTO;
import com.sentinel.api.dto.IncidentCommentDTO;
import com.sentinel.api.dto.IncidentDTO;
import com.sentinel.api.dto.ManualDispositionRequest;
import com.sentinel.api.service.IncidentService;
import com.sentinel.api.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IncidentService incidentService;

    @MockBean
    private SimulationService simulationService;

    @Test
    void getIncidents() throws Exception {
        IncidentDTO incident = IncidentDTO.builder().incidentId(UUID.randomUUID()).build();
        Mockito.when(incidentService.getIncidents(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(incident)));

        mockMvc.perform(get("/api/v1/incidents")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].incidentId").value(incident.getIncidentId().toString()));
    }

    @Test
    void getIncident() throws Exception {
        UUID id = UUID.randomUUID();
        IncidentDTO incident = IncidentDTO.builder().incidentId(id).build();
        Mockito.when(incidentService.getIncident(id)).thenReturn(incident);

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(id.toString()));
    }

    @Test
    void getUnknownIncidents() throws Exception {
        IncidentDTO incident = IncidentDTO.builder().incidentId(UUID.randomUUID()).build();
        Mockito.when(incidentService.getUnknownIncidents()).thenReturn(List.of(incident));

        mockMvc.perform(get("/api/v1/incidents/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].incidentId").value(incident.getIncidentId().toString()));
    }

    @Test
    void getDashboardStats() throws Exception {
        DashboardStatsDTO stats = DashboardStatsDTO.builder().totalIncidents(10L).build();
        Mockito.when(simulationService.isSimulationActive()).thenReturn(true);
        Mockito.when(incidentService.getDashboardStats(true)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/incidents/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncidents").value(10));
    }

    @Test
    void resolveIncident() throws Exception {
        UUID id = UUID.randomUUID();
        IncidentDTO incident = IncidentDTO.builder().incidentId(id).status("RESOLVED").build();
        Mockito.when(incidentService.resolveIncident(id)).thenReturn(incident);

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void dismissIncident() throws Exception {
        UUID id = UUID.randomUUID();
        IncidentDTO incident = IncidentDTO.builder().incidentId(id).status("CLOSED").build();
        Mockito.when(incidentService.dismissIncident(id)).thenReturn(incident);

        mockMvc.perform(post("/api/v1/incidents/{id}/dismiss", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void manualDisposition() throws Exception {
        UUID id = UUID.randomUUID();
        ManualDispositionRequest request = new ManualDispositionRequest();
        request.setRootCause("Database Issue");

        IncidentDTO incident = IncidentDTO.builder().incidentId(id).rootCause("Database Issue").build();
        Mockito.when(incidentService.manualDisposition(eq(id), any(ManualDispositionRequest.class))).thenReturn(incident);

        mockMvc.perform(put("/api/v1/incidents/{id}/manual-disposition", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("Database Issue"));
    }

    @Test
    void acceptIncident() throws Exception {
        UUID id = UUID.randomUUID();
        IncidentDTO incident = IncidentDTO.builder().incidentId(id).status("IN_PROGRESS").build();
        Mockito.when(incidentService.acceptIncident(id)).thenReturn(incident);

        mockMvc.perform(post("/api/v1/incidents/{id}/accept", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void closeIncident() throws Exception {
        UUID id = UUID.randomUUID();
        IncidentDTO incident = IncidentDTO.builder().incidentId(id).status("CLOSED").build();
        Mockito.when(incidentService.closeIncident(id)).thenReturn(incident);

        mockMvc.perform(post("/api/v1/incidents/{id}/close", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void retryAnalysis() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/incidents/{id}/retry-analysis", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("retry_requested"));
        
        Mockito.verify(incidentService).retryAnalysis(id);
    }

    @Test
    void getComments() throws Exception {
        UUID id = UUID.randomUUID();
        IncidentCommentDTO comment = IncidentCommentDTO.builder().incidentId(id).content("Test").build();
        Mockito.when(incidentService.getComments(id)).thenReturn(List.of(comment));

        mockMvc.perform(get("/api/v1/incidents/{id}/comments", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Test"));
    }

    @Test
    void addComment() throws Exception {
        UUID id = UUID.randomUUID();
        CommentRequest request = new CommentRequest();
        request.setAuthor("Analyst");
        request.setContent("Test comment");

        IncidentCommentDTO comment = IncidentCommentDTO.builder()
                .incidentId(id)
                .author("Analyst")
                .content("Test comment")
                .build();
        
        Mockito.when(incidentService.addComment(eq(id), eq("Analyst"), eq("Test comment"))).thenReturn(comment);

        mockMvc.perform(post("/api/v1/incidents/{id}/comments", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("Analyst"))
                .andExpect(jsonPath("$.content").value("Test comment"));
    }
}
