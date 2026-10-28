package com.githubrepoinsight.analyzer;

import com.githubrepoinsight.dto.AnalysisResultDto.*;
import com.githubrepoinsight.github.GitHubTreeEntry;
import lombok.Builder;
import lombok.Data;

import java.util.*;

/**
 * Shared context object passed through all analyzers.
 * Each analyzer enriches this context with its findings.
 */
@Data
@Builder
public class AnalysisContext {

    private String owner;
    private String repoName;
    private String branch;
    private RepositoryInfo repositoryInfo;
    private String readme;

    // Raw file tree from GitHub
    private List<GitHubTreeEntry> treeEntries;

    // Map of filePath -> fileContent (only for analyzed files)
    @Builder.Default
    private Map<String, String> fileContents = new LinkedHashMap<>();

    // Analysis results populated by analyzers
    @Builder.Default
    private List<String> detectedLanguages = new ArrayList<>();

    @Builder.Default
    private List<FrameworkInfo> detectedFrameworks = new ArrayList<>();

    @Builder.Default
    private List<DependencyInfo> detectedDependencies = new ArrayList<>();

    @Builder.Default
    private List<AnalyzedFileDto> analyzedFiles = new ArrayList<>();

    @Builder.Default
    private List<KeyFileInfo> keyFiles = new ArrayList<>();

    private DatabaseInfo databaseInfo;

    @Builder.Default
    private List<TableInfo> databaseTables = new ArrayList<>();

    @Builder.Default
    private List<RelationshipInfo> databaseRelationships = new ArrayList<>();

    // Build-system info
    private String buildSystem; // MAVEN, GRADLE, NONE
    private String pomContent;
    private String gradleContent;
    private String appPropertiesContent;
    private String appYamlContent;

    // Detected project type
    private String projectType; // SPRING_BOOT, PLAIN_JAVA, PYTHON, JS, etc.

    // Error/warning messages
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    // Helper: check if a certain path exists in the tree
    public boolean hasFile(String pathFragment) {
        if (treeEntries == null) return false;
        return treeEntries.stream()
            .anyMatch(e -> e.getPath().contains(pathFragment));
    }

    // Helper: find files by extension
    public List<GitHubTreeEntry> filesWithExtension(String extension) {
        if (treeEntries == null) return List.of();
        return treeEntries.stream()
            .filter(e -> "blob".equals(e.getType()) && e.getPath().endsWith("." + extension))
            .toList();
    }

    // Helper: find files by name
    public Optional<GitHubTreeEntry> findFileByName(String name) {
        if (treeEntries == null) return Optional.empty();
        return treeEntries.stream()
            .filter(e -> "blob".equals(e.getType()) && e.getPath().endsWith("/" + name) || e.getPath().equals(name))
            .findFirst();
    }
}
