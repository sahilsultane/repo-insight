package com.githubrepoinsight.analyzer;

import com.githubrepoinsight.dto.AnalysisResultDto.KeyFileInfo;
import com.githubrepoinsight.github.GitHubService;
import com.githubrepoinsight.github.GitHubTreeEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Fetches and analyzes configuration files: application.properties, application.yml,
 * Dockerfile, docker-compose.yml, README.md, etc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigFileAnalyzer {

    private final GitHubService gitHubService;

    // Files that are always "key files" regardless of language
    private static final Map<String, Integer> KEY_FILE_SCORES = new LinkedHashMap<>();

    static {
        KEY_FILE_SCORES.put("application.properties", 95);
        KEY_FILE_SCORES.put("application.yml", 95);
        KEY_FILE_SCORES.put("application.yaml", 95);
        KEY_FILE_SCORES.put("pom.xml", 90);
        KEY_FILE_SCORES.put("build.gradle", 90);
        KEY_FILE_SCORES.put("build.gradle.kts", 90);
        KEY_FILE_SCORES.put("Dockerfile", 80);
        KEY_FILE_SCORES.put("docker-compose.yml", 80);
        KEY_FILE_SCORES.put("docker-compose.yaml", 80);
        KEY_FILE_SCORES.put("README.md", 75);
        KEY_FILE_SCORES.put("readme.md", 75);
        KEY_FILE_SCORES.put(".env.example", 70);
        KEY_FILE_SCORES.put("schema.sql", 85);
        KEY_FILE_SCORES.put("data.sql", 80);
        KEY_FILE_SCORES.put("init.sql", 80);
    }

    public void analyze(AnalysisContext context) {
        log.info("Analyzing configuration files...");

        // Fetch application.properties
        fetchAndStoreConfig(context, "application.properties", "properties");
        // Fetch application.yml
        fetchAndStoreConfig(context, "application.yml", "yaml");

        // Scan all files for key-file candidates
        for (GitHubTreeEntry entry : context.getTreeEntries()) {
            if (!"blob".equals(entry.getType())) continue;

            String fileName = entry.getPath().substring(entry.getPath().lastIndexOf('/') + 1);
            Integer score = KEY_FILE_SCORES.get(fileName);

            if (score != null) {
                context.getKeyFiles().add(KeyFileInfo.builder()
                    .fileName(fileName)
                    .filePath(entry.getPath())
                    .importanceScore(score)
                    .reason(getKeyFileReason(fileName))
                    .purpose(getKeyFilePurpose(fileName))
                    .build());
            }
        }

        // Also mark main application entry point
        markSpringBootMainClass(context);

        // Mark Controllers, Services, Repositories as key files
        markArchitecturalKeyFiles(context);
    }

    private void fetchAndStoreConfig(AnalysisContext context, String fileName, String type) {
        Optional<GitHubTreeEntry> entry = context.findFileByName(fileName);
        if (entry.isEmpty()) return;

        String content = gitHubService.fetchFileContent(
            context.getOwner(), context.getRepoName(), entry.get().getPath(), context.getBranch());
        if (content == null) return;

        // IMPORTANT: Sanitize sensitive values before storing
        content = sanitizeSensitiveValues(content);

        if ("properties".equals(type)) {
            context.setAppPropertiesContent(content);
        } else {
            context.setAppYamlContent(content);
        }

        log.debug("Fetched config file: {}", fileName);
    }

    /**
     * Redacts sensitive values from config file content.
     * This prevents secrets from being sent to AI or stored carelessly.
     */
    private String sanitizeSensitiveValues(String content) {
        if (content == null) return null;
        // Redact passwords
        content = content.replaceAll("(?i)(password\\s*[=:]\\s*)([^\\n\\r]+)", "$1[REDACTED]");
        // Redact secrets
        content = content.replaceAll("(?i)(secret\\s*[=:]\\s*)([^\\n\\r]+)", "$1[REDACTED]");
        // Redact API keys
        content = content.replaceAll("(?i)(api[.-]?key\\s*[=:]\\s*)([^\\n\\r]+)", "$1[REDACTED]");
        // Redact tokens
        content = content.replaceAll("(?i)(token\\s*[=:]\\s*)([^\\n\\r]+)", "$1[REDACTED]");
        return content;
    }

    private void markSpringBootMainClass(AnalysisContext context) {
        for (var file : context.getAnalyzedFiles()) {
            if (file.getClasses() == null) continue;
            for (var cls : file.getClasses()) {
                if (cls.getAnnotations() != null
                    && cls.getAnnotations().contains("@SpringBootApplication")) {
                    boolean exists = context.getKeyFiles().stream()
                        .anyMatch(k -> k.getFilePath().equals(file.getFilePath()));
                    if (!exists) {
                        context.getKeyFiles().add(KeyFileInfo.builder()
                            .fileName(file.getFileName())
                            .filePath(file.getFilePath())
                            .importanceScore(98)
                            .reason("Spring Boot main application entry point")
                            .purpose("Contains the main() method and @SpringBootApplication annotation that bootstraps the entire application.")
                            .build());
                    }
                }
            }
        }
    }

    private void markArchitecturalKeyFiles(AnalysisContext context) {
        // Count per role to limit how many we add
        Map<String, Integer> roleCounts = new HashMap<>();

        for (var file : context.getAnalyzedFiles()) {
            if (file.getClasses() == null) continue;
            for (var cls : file.getClasses()) {
                String role = cls.getSpringRole();
                if (role == null) continue;

                int count = roleCounts.getOrDefault(role, 0);
                if (count >= 3) continue; // Max 3 per role

                int score = switch (role) {
                    case "CONTROLLER" -> 85;
                    case "SERVICE" -> 80;
                    case "REPOSITORY" -> 75;
                    case "ENTITY" -> 70;
                    default -> 50;
                };

                boolean exists = context.getKeyFiles().stream()
                    .anyMatch(k -> k.getFilePath().equals(file.getFilePath()));
                if (!exists) {
                    context.getKeyFiles().add(KeyFileInfo.builder()
                        .fileName(file.getFileName())
                        .filePath(file.getFilePath())
                        .importanceScore(score)
                        .reason(role + " — architectural component")
                        .purpose(file.getPurpose())
                        .build());
                    roleCounts.put(role, count + 1);
                }
            }
        }

        // Sort key files by importance score
        context.getKeyFiles().sort(Comparator.comparingInt(KeyFileInfo::getImportanceScore).reversed());
    }

    private String getKeyFileReason(String fileName) {
        return switch (fileName) {
            case "application.properties", "application.yml", "application.yaml" ->
                "Main application configuration file — defines database, server, and framework settings.";
            case "pom.xml" ->
                "Maven build file — defines all project dependencies, plugins, and build configuration.";
            case "build.gradle", "build.gradle.kts" ->
                "Gradle build file — defines all project dependencies and build tasks.";
            case "Dockerfile" ->
                "Docker image definition — describes how to containerize the application.";
            case "docker-compose.yml", "docker-compose.yaml" ->
                "Docker Compose configuration — defines the full multi-container application stack.";
            case "README.md", "readme.md" ->
                "Project documentation — provides an overview, setup instructions, and usage.";
            case "schema.sql", "data.sql", "init.sql" ->
                "Database initialization script — defines the schema and/or seed data.";
            default -> "Important project file.";
        };
    }

    private String getKeyFilePurpose(String fileName) {
        return switch (fileName) {
            case "application.properties", "application.yml" ->
                "Configures the Spring Boot application: database connection, port, JPA settings, security, and other properties.";
            case "pom.xml" ->
                "Declares all Maven dependencies (Spring Boot, JPA, security, etc.), parent POM, and build plugins.";
            case "build.gradle" ->
                "Declares all Gradle dependencies and build tasks for the project.";
            case "Dockerfile" ->
                "Contains instructions to build a Docker image of the backend application.";
            case "docker-compose.yml", "docker-compose.yaml" ->
                "Orchestrates multiple Docker containers (backend, frontend, database) as a unified application.";
            case "schema.sql" ->
                "SQL DDL script that creates the initial database schema.";
            default -> "Important project configuration or documentation file.";
        };
    }
}
