package com.githubrepoinsight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubrepoinsight.dto.AnalysisResultDto;
import com.githubrepoinsight.dto.AnalysisStatusResponse;
import com.githubrepoinsight.exception.AnalysisException;
import com.githubrepoinsight.github.GitHubService;
import com.githubrepoinsight.model.Analysis;
import com.githubrepoinsight.model.Analysis.AnalysisStatus;
import com.githubrepoinsight.repository.AnalysisRepository;
import com.githubrepoinsight.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final GitHubService gitHubService;
    private final AnalysisRepository analysisRepository;
    private final RepositoryRepository repositoryRepository;
    private final AsyncAnalysisRunner asyncAnalysisRunner; // separate bean — @Async works correctly
    private final ObjectMapper objectMapper;

    /**
     * Creates DB records and immediately returns the analysisId.
     * Actual analysis runs in a background thread via AsyncAnalysisRunner.
     * This method must return in < 500ms.
     */
    @Transactional
    public String startAnalysis(String repositoryUrl, Long userId) {
        String[] ownerRepo = gitHubService.parseGitHubUrl(repositoryUrl);
        String owner = ownerRepo[0];
        String repoName = ownerRepo[1];
        String analysisId = UUID.randomUUID().toString();

        com.githubrepoinsight.model.Repository repoRecord =
            repositoryRepository.findFirstByOwnerAndRepoName(owner, repoName)
                .orElseGet(() -> repositoryRepository.save(
                    com.githubrepoinsight.model.Repository.builder()
                        .githubUrl("https://github.com/" + owner + "/" + repoName)
                        .owner(owner).repoName(repoName).build()));

        Analysis analysis = analysisRepository.save(Analysis.builder()
            .analysisId(analysisId)
            .status(AnalysisStatus.PENDING)
            .statusMessage("Analysis queued...")
            .repository(repoRecord)
            .build());

        // Fire-and-forget via @Async proxy (separate bean — proxy is honoured)
        asyncAnalysisRunner.run(analysisId, owner, repoName, repoRecord.getId());

        log.info("Analysis {} queued for {}/{}", analysisId, owner, repoName);
        return analysisId;
    }

    @Transactional(readOnly = true)
    public AnalysisResultDto getAnalysisResult(String analysisId) {
        Analysis analysis = analysisRepository.findByAnalysisId(analysisId)
            .orElseThrow(() -> new AnalysisException("Analysis not found: " + analysisId));

        if (analysis.getStatus() == AnalysisStatus.FAILED) {
            return AnalysisResultDto.builder()
                .analysisId(analysisId).status(AnalysisStatus.FAILED)
                .errorMessage(analysis.getErrorMessage())
                .createdAt(analysis.getCreatedAt()).build();
        }

        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            return AnalysisResultDto.builder()
                .analysisId(analysisId).status(analysis.getStatus())
                .statusMessage(analysis.getStatusMessage())
                .createdAt(analysis.getCreatedAt()).build();
        }

        try {
            return objectMapper.readValue(analysis.getFullAnalysisJson(), AnalysisResultDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize analysis result: {}", e.getMessage());
            throw new AnalysisException("Failed to retrieve analysis result");
        }
    }

    @Transactional(readOnly = true)
    public AnalysisStatusResponse getStatus(String analysisId) {
        Analysis analysis = analysisRepository.findByAnalysisId(analysisId)
            .orElseThrow(() -> new AnalysisException("Analysis not found: " + analysisId));

        return AnalysisStatusResponse.builder()
            .analysisId(analysisId).status(analysis.getStatus())
            .statusMessage(analysis.getStatusMessage())
            .errorMessage(analysis.getErrorMessage())
            .createdAt(analysis.getCreatedAt())
            .completedAt(analysis.getCompletedAt()).build();
    }
}
