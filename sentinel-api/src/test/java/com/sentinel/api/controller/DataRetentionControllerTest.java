package com.sentinel.api.controller;

import com.sentinel.api.service.DataRetentionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataRetentionController.class)
class DataRetentionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataRetentionService dataRetentionService;

    @Test
    void triggerCleanup() throws Exception {
        mockMvc.perform(post("/api/v1/system/retention-cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));
        
        Mockito.verify(dataRetentionService).triggerManualCleanup();
    }
}
