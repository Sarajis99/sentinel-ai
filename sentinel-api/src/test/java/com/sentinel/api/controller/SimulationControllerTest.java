package com.sentinel.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.api.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SimulationService simulationService;

    @Test
    void triggerSimulation_Started() throws Exception {
        Mockito.when(simulationService.triggerSimulation()).thenReturn(true);

        mockMvc.perform(post("/api/v1/system/simulate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"));
    }

    @Test
    void triggerSimulation_Conflict() throws Exception {
        Mockito.when(simulationService.triggerSimulation()).thenReturn(false);

        mockMvc.perform(post("/api/v1/system/simulate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("already_running"));
    }

    @Test
    void stopSimulation_Stopped() throws Exception {
        Mockito.when(simulationService.stopSimulation()).thenReturn(true);

        mockMvc.perform(post("/api/v1/system/simulate/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stopped"));
    }

    @Test
    void stopSimulation_NotRunning() throws Exception {
        Mockito.when(simulationService.stopSimulation()).thenReturn(false);

        mockMvc.perform(post("/api/v1/system/simulate/stop"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("not_running"));
    }

    @Test
    void getSimulationStatus_Active() throws Exception {
        Mockito.when(simulationService.isSimulationActive()).thenReturn(true);

        mockMvc.perform(get("/api/v1/system/simulation-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getSimulationStatus_Inactive() throws Exception {
        Mockito.when(simulationService.isSimulationActive()).thenReturn(false);

        mockMvc.perform(get("/api/v1/system/simulation-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
