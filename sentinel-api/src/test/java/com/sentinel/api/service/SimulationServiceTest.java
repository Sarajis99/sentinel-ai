package com.sentinel.api.service;

import com.sentinel.common.dto.LogEventDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    
    @Mock
    private KafkaTemplate<String, LogEventDTO> kafkaTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SimulationService simulationService;

    @Test
    void triggerSimulation_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("simulator:active_lock"), eq("running"), any(Duration.class)))
                .thenReturn(true);
        when(valueOperations.get("simulator:logs-per-second")).thenReturn("10");

        boolean result = simulationService.triggerSimulation();

        assertTrue(result);
    }

    @Test
    void triggerSimulation_AlreadyRunning() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("simulator:active_lock"), eq("running"), any(Duration.class)))
                .thenReturn(false);

        boolean result = simulationService.triggerSimulation();

        assertFalse(result);
    }

    @Test
    void stopSimulation_NotActive() {
        when(redisTemplate.hasKey("simulator:active_lock")).thenReturn(false);

        boolean result = simulationService.stopSimulation();

        assertFalse(result);
    }

    @Test
    void stopSimulation_Active() {
        when(redisTemplate.hasKey("simulator:active_lock")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        
        simulationService.triggerSimulation(); // to start tasks
        
        boolean result = simulationService.stopSimulation();
        assertTrue(result);
        verify(redisTemplate).delete("simulator:active_lock");
    }

    @Test
    void isSimulationActive() {
        when(redisTemplate.hasKey("simulator:active_lock")).thenReturn(true);
        assertTrue(simulationService.isSimulationActive());
    }
}
