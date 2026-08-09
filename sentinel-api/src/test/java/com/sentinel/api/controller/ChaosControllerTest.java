package com.sentinel.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.api.service.ChaosService;
import com.sentinel.common.dto.LogEventDTO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChaosController.class)
class ChaosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChaosService chaosService;

    @Test
    void injectChaos_Success() throws Exception {
        ChaosController.ChaosInjectionRequest request = new ChaosController.ChaosInjectionRequest();
        request.setScenario("ERROR_SPIKE");
        request.setTargetService("payment-service");

        LogEventDTO event = LogEventDTO.builder().eventId("event1").build();
        Mockito.when(chaosService.injectAnomaly("ERROR_SPIKE", "payment-service"))
                .thenReturn(List.of(event));

        mockMvc.perform(post("/api/v1/chaos/inject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.events[0].eventId").value("event1"));
    }

    @Test
    void injectChaos_SimulationNotActive() throws Exception {
        ChaosController.ChaosInjectionRequest request = new ChaosController.ChaosInjectionRequest();
        request.setScenario("ERROR_SPIKE");
        request.setTargetService("payment-service");

        Mockito.when(chaosService.injectAnomaly("ERROR_SPIKE", "payment-service"))
                .thenThrow(new IllegalStateException("Simulation is not active"));

        mockMvc.perform(post("/api/v1/chaos/inject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Simulation is not active"));
    }

    @Test
    void injectChaos_InvalidScenario() throws Exception {
        ChaosController.ChaosInjectionRequest request = new ChaosController.ChaosInjectionRequest();
        request.setScenario("INVALID");
        request.setTargetService("payment-service");

        Mockito.when(chaosService.injectAnomaly("INVALID", "payment-service"))
                .thenThrow(new IllegalArgumentException("Invalid scenario"));

        mockMvc.perform(post("/api/v1/chaos/inject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid scenario or service."));
    }

    @Test
    void getScenarios() throws Exception {
        Mockito.when(chaosService.getAvailableScenarios())
                .thenReturn(List.of(Map.of("id", "ERROR_SPIKE", "label", "Error Spike")));

        mockMvc.perform(get("/api/v1/chaos/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ERROR_SPIKE"));
    }

    @Test
    void getServices() throws Exception {
        Mockito.when(chaosService.getAvailableServices())
                .thenReturn(List.of("payment-service"));

        mockMvc.perform(get("/api/v1/chaos/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("payment-service"));
    }
}
