package com.sentinel.rca.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.rca.model.RCAResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenRouterClientTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private ObjectMapper objectMapper = new ObjectMapper();
    private OpenRouterClient openRouterClient;

    @BeforeEach
    void setUp() {
        openRouterClient = new OpenRouterClient(objectMapper, redisTemplate);
        ReflectionTestUtils.setField(openRouterClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(openRouterClient, "defaultApiKey", "test-key");
        ReflectionTestUtils.setField(openRouterClient, "baseUrl", "http://test-url");
        ReflectionTestUtils.setField(openRouterClient, "defaultModel", "test-model");
    }

    @Test
    void testIsAvailable_True() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        assertTrue(openRouterClient.isAvailable());
    }

    @Test
    void testGenerateRCA_Success() throws Exception {
        String jsonResponse = "{\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"message\": {\n" +
                "        \"content\": \"{\\\"rootCause\\\":\\\"DB_TIMEOUT\\\",\\\"title\\\":\\\"Test\\\",\\\"rcaSummary\\\":\\\"Sum\\\",\\\"confidence\\\":0.9}\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        
        ResponseEntity<String> responseEntity = new ResponseEntity<>(jsonResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        RCAResponse response = openRouterClient.generateRCA("test prompt");

        assertTrue(response.isParseSuccess());
        assertEquals("DB_TIMEOUT", response.getRootCause());
        assertEquals(0.9, response.getConfidence());
    }

    @Test
    void testGenerateRCA_ParseError() {
        String invalidJsonResponse = "invalid json";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        ResponseEntity<String> responseEntity = new ResponseEntity<>(invalidJsonResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        RCAResponse response = openRouterClient.generateRCA("test prompt");

        assertFalse(response.isParseSuccess());
        assertEquals("UNKNOWN", response.getRootCause());
    }
}
