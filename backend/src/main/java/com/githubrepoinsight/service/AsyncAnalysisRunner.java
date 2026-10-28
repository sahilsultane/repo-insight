package com.githubrepoinsight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubrepoinsight.ai.AiExplanationService;
import com.githubrepoinsight.analyzer.*;
import com.githubrepoinsight.dto.AnalysisResultDto;
import com.githubrepoinsight.dto.AnalysisResultDto.*;
import com.githubrepoinsight.exception.AnalysisException;
import com.githubrepoinsight.exception.RepositoryNotFoundException;
import com.githubrepoinsight.github.GitHubService;
import com.githubrepoinsight.github.GitHubTreeEntry;
import com.githubrepoinsight.model.Analysis;
import com.githubrepoinsight.model.Analysis.AnalysisStatus;
import com.githubrepoinsight.model.AnalyzedFile;
import com.githubrepoinsight.model.DatabaseColumn;
import com.githubrepoinsight.model.DatabaseTable;
import com.githubrepoinsight.repository.AnalysisRepository;
import com.githubrepoinsight.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Separate bean so @Async is honoured by Spring's proxy.
 * Calling @Async from within the same bean bypasses the proxy — always use a separate bean.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncAnalysisRunner {

    private final GitHubService gitHubService;
    private final LanguageDetector languageDetector;
    private final BuildSystemAnalyzer buildSystemAnalyzer;
    private final JavaAstAnalyzer javaAstAnalyzer;
    private final DatabaseAnalyzer databaseAnalyzer;
    private final ConfigFileAnalyzer configFileAnalyzer;
    private final ArchitectureGraphBuilder architectureGraphBuilder;
    private final FileTreeBuilder fileTreeBuilder;
    private final AiExplanationService aiExplanationService;
    private final UniversalFileAnalyzer universalFileAnalyzer;
    private final AnalysisRepository analysisRepository;
    private final RepositoryRepository repositoryRepository;
    private final ObjectMapper objectMapper;

    @Async("analysisExecutor")
    public void run(String analysisId, String owner, String repoName, Long repoId) {
        log.info("=== ASYNC analysis started for {}/{} id={} ===", owner, repoName, analysisId);

        try {
            updateStatus(analysisId, AnalysisStatus.FETCHING, "Fetching repository metadata...");

            RepositoryInfo repoInfo = gitHubService.fetchRepositoryInfo(owner, repoName);
            String branch = repoInfo.getDefaultBranch();
            updateRepositoryRecord(repoId, repoInfo);

            updateStatus(analysisId, AnalysisStatus.FETCHING, "Fetching repository file tree...");
            List<GitHubTreeEntry> treeEntries = gitHubService.fetchRepositoryTree(owner, repoName, branch);

            String readme = gitHubService.fetchReadme(owner, repoName);

            AnalysisContext context = AnalysisContext.builder()
                .owner(owner).repoName(repoName).branch(branch)
                .repositoryInfo(repoInfo).readme(readme).treeEntries(treeEntries)
                .build();

            updateStatus(analysisId, AnalysisStatus.ANALYZING, "Detecting programming languages...");
            languageDetector.detect(context);

            updateStatus(analysisId, AnalysisStatus.ANALYZING, "Analyzing build system and dependencies...");
            buildSystemAnalyzer.analyze(context);

            updateStatus(analysisId, AnalysisStatus.ANALYZING, "Analyzing configuration files...");
            configFileAnalyzer.analyze(context);

            updateStatus(analysisId, AnalysisStatus.ANALYZING, "Analyzing Java source files (AST)...");
            javaAstAnalyzer.analyze(context);

            updateStatus(analysisId, AnalysisStatus.ANALYZING, "Analyzing all other file types...");
            universalFileAnalyzer.analyze(context);

            updateStatus(analysisId, AnalysisStatus.ANALYZING, "Detecting database schema...");
            databaseAnalyzer.analyze(context);

            updateStatus(analysisId, AnalysisStatus.ANALYZING, "Building architecture diagram...");
            GraphData archGraph = architectureGraphBuilder.buildArchitectureGraph(context);

            updateStatus(analysisId, AnalysisStatus.ANALYZING, "Building ER diagram...");
            GraphData erDiagram = architectureGraphBuilder.buildErDiagram(context);

            FileTreeNode fileTree = fileTreeBuilder.build(context);

            updateStatus(analysisId, AnalysisStatus.GENERATING, "Generating AI project summary...");
            String projectSummary = aiExplanationService.generateProjectSummary(context);

            updateStatus(analysisId, AnalysisStatus.GENERATING, "Generating AI architecture explanation...");
            String archExplanation = aiExplanationService.generateArchitectureExplanation(context);

            updateStatus(analysisId, AnalysisStatus.GENERATING, "Generating AI database explanation...");
            String dbExplanation = aiExplanationService.generateDatabaseExplanation(context);

            updateStatus(analysisId, AnalysisStatus.GENERATING, "Generating AI how-it-works explanation...");
            String howItWorks = aiExplanationService.generateHowItWorks(context);

            // AI explanations for key files (top 10) — ALL file types
            updateStatus(analysisId, AnalysisStatus.GENERATING, "Generating per-file AI explanations...");
            List<KeyFileInfo> keyFiles = context.getKeyFiles().stream().limit(12).collect(Collectors.toList());
            for (KeyFileInfo kf : keyFiles) {
                context.getAnalyzedFiles().stream()
                    .filter(f -> f.getFilePath().equals(kf.getFilePath()))
                    .findFirst()
                    .ifPresent(file -> {
                        try {
                            // Enrich file with actual content before AI call
                            if (file.getPurpose() == null || file.getPurpose().isBlank()) return;
                            String explanation = aiExplanationService.generateFileExplanation(file, repoName);
                            kf.setAiExplanation(explanation);
                            file.setAiExplanation(explanation);
                        } catch (Exception e) {
                            log.warn("File AI failed for {}: {}", kf.getFileName(), e.getMessage());
                        }
                    });
            }

            // AI explanations for ALL analyzed files that have a purpose but no explanation yet
            // (covers both Java architectural files AND non-Java files)
            for (AnalyzedFileDto fileDto : context.getAnalyzedFiles()) {
                if (fileDto.getAiExplanation() != null) continue; // already has one
                if (fileDto.getPurpose() == null || fileDto.getPurpose().isBlank()) continue;
                // Only generate for files that have meaningful content (purpose set by analyzer)
                boolean isArchitectural = fileDto.getClasses() != null &&
                    fileDto.getClasses().stream().anyMatch(c -> c.getSpringRole() != null);
                boolean isImportantNonJava = fileDto.getFileType() != null &&
                    List.of("CONFIG", "BUILD", "DOCUMENTATION", "DATABASE", "INFRASTRUCTURE").contains(fileDto.getFileType());
                if (!isArchitectural && !isImportantNonJava) continue;
                try {
                    fileDto.setAiExplanation(aiExplanationService.generateFileExplanation(fileDto, repoName));
                } catch (Exception e) {
                    log.warn("File AI failed for {}: {}", fileDto.getFileName(), e.getMessage());
                }
            }

            updateStatus(analysisId, AnalysisStatus.GENERATING, "Saving results to database...");

            AnalysisResultDto result = AnalysisResultDto.builder()
                .analysisId(analysisId)
                .status(AnalysisStatus.COMPLETED)
                .repositoryInfo(repoInfo)
                .projectSummary(projectSummary)
                .architectureExplanation(archExplanation)
                .databaseExplanation(dbExplanation)
                .howItWorksExplanation(howItWorks)
                .detectedLanguages(context.getDetectedLanguages())
                .detectedFrameworks(context.getDetectedFrameworks())
                .detectedDependencies(context.getDetectedDependencies())
                .fileTree(fileTree)
                .keyFiles(keyFiles)
                .analyzedFiles(context.getAnalyzedFiles())
                .databaseInfo(context.getDatabaseInfo())
                .databaseTables(context.getDatabaseTables())
                .architectureGraph(archGraph)
                .erDiagram(erDiagram)
                .completedAt(LocalDateTime.now())
                .build();

            persistResult(analysisId, result, context);
            log.info("=== ASYNC analysis COMPLETED for {}/{} ===", owner, repoName);

        } catch (RepositoryNotFoundException e) {
            log.warn("Repository not found: {}", e.getMessage());
            failAnalysis(analysisId, "Repository not found or is private: " + e.getMessage());
        } catch (Exception e) {
            log.error("Analysis failed for {}/{}: {}", owner, repoName, e.getMessage(), e);
            failAnalysis(analysisId, "Analysis failed: " + e.getMessage());
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void updateStatus(String analysisId, AnalysisStatus status, String message) {
        try {
            analysisRepository.findByAnalysisId(analysisId).ifPresent(a -> {
                a.setStatus(status);
                a.setStatusMessage(message);
                analysisRepository.save(a);
            });
            log.debug("[{}] {} — {}", analysisId, status, message);
        } catch (Exception e) {
            log.warn("Could not update status: {}", e.getMessage());
        }
    }

    private void failAnalysis(String analysisId, String errorMessage) {
        try {
            analysisRepository.findByAnalysisId(analysisId).ifPresent(a -> {
                a.setStatus(AnalysisStatus.FAILED);
                a.setErrorMessage(errorMessage);
                a.setStatusMessage("Analysis failed");
                analysisRepository.save(a);
            });
        } catch (Exception e) {
            log.warn("Could not mark analysis failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void persistResult(String analysisId, AnalysisResultDto result, AnalysisContext context) {
        Analysis analysis = analysisRepository.findByAnalysisId(analysisId)
            .orElseThrow(() -> new AnalysisException("Analysis not found: " + analysisId));
        try {
            analysis.setStatus(AnalysisStatus.COMPLETED);
            analysis.setStatusMessage("Completed successfully");
            analysis.setProjectSummary(result.getProjectSummary());
            analysis.setArchitectureExplanation(result.getArchitectureExplanation());
            analysis.setDatabaseExplanation(result.getDatabaseExplanation());
            analysis.setHowItWorksExplanation(result.getHowItWorksExplanation());
            analysis.setCompletedAt(result.getCompletedAt());
            analysis.setFullAnalysisJson(objectMapper.writeValueAsString(result));

            // Persist DB tables
            for (TableInfo ti : context.getDatabaseTables()) {
                DatabaseTable table = DatabaseTable.builder()
                    .tableName(ti.getTableName()).entityClass(ti.getEntityClass())
                    .sourceFile(ti.getSourceFile()).detectionMethod(ti.getDetectionMethod())
                    .analysis(analysis).build();
                if (ti.getColumns() != null) {
                    for (ColumnInfo ci : ti.getColumns()) {
                        table.getColumns().add(DatabaseColumn.builder()
                            .columnName(ci.getColumnName()).dataType(ci.getDataType())
                            .isPrimaryKey(ci.isPrimaryKey()).isForeignKey(ci.isForeignKey())
                            .isNullable(ci.isNullable()).isUnique(ci.isUnique())
                            .referencedTable(ci.getReferencedTable()).referencedColumn(ci.getReferencedColumn())
                            .databaseTable(table).build());
                    }
                }
                analysis.getDatabaseTables().add(table);
            }

            // Persist key files
            context.getKeyFiles().stream().limit(20).forEach(kf ->
                context.getAnalyzedFiles().stream()
                    .filter(f -> f.getFilePath().equals(kf.getFilePath()))
                    .findFirst().ifPresent(fd -> {
                        try {
                            analysis.getFiles().add(AnalyzedFile.builder()
                                .fileName(fd.getFileName()).filePath(fd.getFilePath())
                                .language(fd.getLanguage()).fileType(fd.getFileType())
                                .isKeyFile(true).importanceScore(kf.getImportanceScore())
                                .purpose(fd.getPurpose()).aiExplanation(kf.getAiExplanation())
                                .classesJson(objectMapper.writeValueAsString(fd.getClasses()))
                                .importsJson(objectMapper.writeValueAsString(fd.getImports()))
                                .endpointsJson(objectMapper.writeValueAsString(fd.getEndpoints()))
                                .analysis(analysis).build());
                        } catch (Exception e) {
                            log.warn("Could not persist file {}", fd.getFilePath());
                        }
                    }));

            analysisRepository.save(analysis);
        } catch (Exception e) {
            log.error("Failed to persist result: {}", e.getMessage(), e);
            throw new AnalysisException("Failed to save: " + e.getMessage());
        }
    }

    @Transactional
    public void updateRepositoryRecord(Long repoId, RepositoryInfo info) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setDescription(info.getDescription());
            repo.setDefaultBranch(info.getDefaultBranch());
            repo.setLanguage(info.getPrimaryLanguage());
            repo.setStars(info.getStars()); repo.setForks(info.getForks());
            repo.setOpenIssues(info.getOpenIssues()); repo.setLicense(info.getLicense());
            repo.setGithubCreatedAt(info.getCreatedAt()); repo.setGithubUpdatedAt(info.getUpdatedAt());
            repo.setSizeKb(info.getSizeKb()); repo.setPrivate(info.isPrivate());
            repositoryRepository.save(repo);
        });
    }
}
