package com.githubrepoinsight.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class OpenAiProvider implements AiProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public OpenAiProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API key not configured.");
            return "Configure OPENAI_API_KEY environment variable to enable AI explanations.";
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            root.put("temperature", 0.3);
            root.put("max_tokens", 2048);

            ArrayNode messages = root.putArray("messages");

            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);

            String response = webClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(root))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode responseNode = objectMapper.readTree(response);
            return responseNode
                .path("choices").get(0)
                .path("message").path("content").asText("No response generated.");

        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            return "AI explanation could not be generated. (OpenAI error: " + e.getMessage() + ")";
        }
    }

    @Override
    public String getProviderName() {
        return "OpenAI";
    }
}
