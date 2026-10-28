package com.githubrepoinsight.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class GeminiProvider implements AiProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    // gemini-flash-lite-latest — stable model confirmed working with AQ. auth keys
    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent";

    public GeminiProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return "ℹ️ AI explanation not available — add GEMINI_API_KEY to your .env file. "
                + "Get a free key at: https://aistudio.google.com/app/apikey";
        }

        try {
            // Build Gemini request body
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");

            ObjectNode turn = contents.addObject();
            turn.put("role", "user");
            ArrayNode parts = turn.putArray("parts");
            // Combine system + user prompt into single user turn
            parts.addObject().put("text", systemPrompt + "\n\n" + userPrompt);

            // Keep responses focused and concise
            ObjectNode genConfig = root.putObject("generationConfig");
            genConfig.put("temperature", 0.3);
            genConfig.put("maxOutputTokens", 2048);

            String requestBody = objectMapper.writeValueAsString(root);
            log.debug("Calling Gemini API (key prefix: {})", apiKey.substring(0, Math.min(6, apiKey.length())));

            // Use x-goog-api-key header — works for BOTH AQ. (new auth keys) and AIza (legacy keys)
            String response = webClient.post()
                .uri(GEMINI_URL)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode responseNode = objectMapper.readTree(response);

            // Handle API-level errors returned in the response body
            if (responseNode.has("error")) {
                String errMsg  = responseNode.path("error").path("message").asText("Unknown error");
                int    errCode = responseNode.path("error").path("code").asInt(0);
                log.error("Gemini API returned error {}: {}", errCode, errMsg);
                return "ℹ️ Gemini API error (" + errCode + "): " + errMsg;
            }

            JsonNode candidates = responseNode.path("candidates");
            if (candidates.isEmpty()) {
                log.warn("Gemini returned no candidates");
                return "ℹ️ Gemini returned an empty response.";
            }

            return candidates.get(0)
                .path("content").path("parts").get(0)
                .path("text").asText("No response text.");

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            return "ℹ️ AI explanation unavailable: " + e.getMessage();
        }
    }

    @Override
    public String getProviderName() {
        return "Google Gemini";
    }
}
