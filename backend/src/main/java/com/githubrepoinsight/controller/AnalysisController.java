package com.githubrepoinsight.controller;

import com.githubrepoinsight.dto.AnalysisRequest;
import com.githubrepoinsight.dto.AnalysisResultDto;
import com.githubrepoinsight.dto.AnalysisStatusResponse;
import com.githubrepoinsight.dto.ChatRequest;
import com.githubrepoinsight.dto.ChatResponse;
import com.githubrepoinsight.service.AnalysisService;
import com.githubrepoinsight.service.ChatContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final ChatContextService chatContextService;

    /**
     * POST /api/repositories/analyze
     * Start a new analysis for a GitHub repository URL.
     */
    @PostMapping("/repositories/analyze")
    public ResponseEntity<Map<String, String>> startAnalysis(
        @Valid @RequestBody AnalysisRequest request
    ) {
        String analysisId = analysisService.startAnalysis(request.getRepositoryUrl(), null);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Map.of(
                "analysisId", analysisId,
                "status", "PROCESSING",
                "message", "Analysis started. Poll /api/analyses/" + analysisId + " for results."
            ));
    }

    /**
     * GET /api/analyses/{id}
     * Get full analysis result (or current status if still processing).
     */
    @GetMapping("/analyses/{id}")
    public ResponseEntity<AnalysisResultDto> getAnalysis(@PathVariable String id) {
        AnalysisResultDto result = analysisService.getAnalysisResult(id);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/analyses/{id}/status
     * Lightweight status polling endpoint.
     */
    @GetMapping("/analyses/{id}/status")
    public ResponseEntity<AnalysisStatusResponse> getStatus(@PathVariable String id) {
        AnalysisStatusResponse status = analysisService.getStatus(id);
        return ResponseEntity.ok(status);
    }

    /**
     * POST /api/analyses/{id}/ask
     * Ask the AI a question about the analyzed repository.
     */
    @PostMapping("/analyses/{id}/ask")
    public ResponseEntity<ChatResponse> askQuestion(
        @PathVariable String id,
        @Valid @RequestBody ChatRequest request
    ) {
        ChatResponse response = chatContextService.answerQuestion(id, request.getQuestion());
        return ResponseEntity.ok(response);
    }
}
