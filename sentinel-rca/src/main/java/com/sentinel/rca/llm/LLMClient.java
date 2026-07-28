package com.sentinel.rca.llm;

import com.sentinel.rca.model.RCAResponse;

/**
 * LLM client abstraction — allows swapping between OpenRouter and Ollama
 * without changing the RCAService orchestrator.
 */
public interface LLMClient {

    /**
     * Send a prompt to the LLM and parse the structured JSON response into an RCAResponse.
     *
     * @param prompt The full prompt including system context and log data
     * @return Parsed RCA response from the LLM
     */
    RCAResponse generateRCA(String prompt);

    /**
     * Returns the name of this provider for logging/metrics purposes.
     */
    String getProviderName();

    /**
     * Check if this LLM client is available/reachable.
     */
    boolean isAvailable();
}
