package com.githubrepoinsight.ai;

/**
 * Contract for AI explanation providers.
 * New providers (Anthropic, Mistral, etc.) can be added by implementing this interface.
 */
public interface AiProvider {

    /**
     * Send a prompt to the AI and return the text response.
     *
     * @param systemPrompt Instructions for the AI model.
     * @param userPrompt   The actual user question or analysis request.
     * @return AI-generated text response.
     */
    String generate(String systemPrompt, String userPrompt);

    /**
     * Provider name for logging and display.
     */
    String getProviderName();
}
