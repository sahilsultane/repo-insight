package com.githubrepoinsight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubrepoinsight.ai.AiExplanationService;
import com.githubrepoinsight.analyzer.AnalysisContext;
import com.githubrepoinsight.dto.AnalysisResultDto;
import com.githubrepoinsight.dto.ChatResponse;
import com.githubrepoinsight.exception.AnalysisException;
import com.githubrepoinsight.model.Analysis;
import com.githubrepoinsight.repository.AnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatContextService {

    private final AnalysisRepository analysisRepository;
    private final AiExplanationService aiExplanationService;
    private final ObjectMapper objectMapper;

    public ChatResponse answerQuestion(String analysisId, String question) {
        Analysis analysis = analysisRepository.findByAnalysisId(analysisId)
            .orElseThrow(() -> new AnalysisException("Analysis not found: " + analysisId));

        if (analysis.getStatus() != Analysis.AnalysisStatus.COMPLETED) {
            return ChatResponse.builder()
                .question(question)
                .answer("Analysis is not yet complete. Please wait for the analysis to finish.")
                .analysisId(analysisId)
                .build();
        }

        if (analysis.getFullAnalysisJson() == null) {
            return ChatResponse.builder()
                .question(question)
                .answer("Analysis data is not available.")
                .analysisId(analysisId)
                .build();
        }

        try {
            // Reconstruct context from stored analysis
            AnalysisResultDto result = objectMapper.readValue(
                analysis.getFullAnalysisJson(), AnalysisResultDto.class);

            // Build a lightweight AnalysisContext from the stored result
            AnalysisContext context = AnalysisContext.builder()
                .owner(result.getRepositoryInfo() != null ? result.getRepositoryInfo().getOwner() : "unknown")
                .repoName(result.getRepositoryInfo() != null ? result.getRepositoryInfo().getName() : "unknown")
                .repositoryInfo(result.getRepositoryInfo())
                .detectedLanguages(result.getDetectedLanguages() != null ? result.getDetectedLanguages() : java.util.List.of())
                .detectedFrameworks(result.getDetectedFrameworks() != null ? result.getDetectedFrameworks() : java.util.List.of())
                .detectedDependencies(result.getDetectedDependencies() != null ? result.getDetectedDependencies() : java.util.List.of())
                .analyzedFiles(result.getAnalyzedFiles() != null ? result.getAnalyzedFiles() : java.util.List.of())
                .databaseInfo(result.getDatabaseInfo())
                .databaseTables(result.getDatabaseTables() != null ? result.getDatabaseTables() : java.util.List.of())
                .build();

            String answer = aiExplanationService.answerQuestion(context, question);

            return ChatResponse.builder()
                .question(question)
                .answer(answer)
                .analysisId(analysisId)
                .build();

        } catch (Exception e) {
            log.error("Error answering question for analysis {}: {}", analysisId, e.getMessage());
            return ChatResponse.builder()
                .question(question)
                .answer("Could not process your question at this time: " + e.getMessage())
                .analysisId(analysisId)
                .build();
        }
    }
}
