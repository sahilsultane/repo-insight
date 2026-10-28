package com.githubrepoinsight.ai;

import com.githubrepoinsight.analyzer.AnalysisContext;
import com.githubrepoinsight.dto.AnalysisResultDto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates AI explanations using structured, detailed prompts.
 * Only sends sanitized structured data — never raw source code secrets.
 */
@Service
@Slf4j
public class AiExplanationService {

    private final AiProvider aiProvider;

    private static final String SYSTEM_PROMPT = """
        You are an expert software engineer and technical writer explaining a GitHub repository.
        
        Rules you MUST follow:
        - Be detailed, thorough, and technically accurate
        - Explain concepts so both beginners AND experienced developers can understand
        - Always mention actual class names, file names, and table names from the data provided
        - Use clear structure: explain WHAT it does, HOW it works, and WHY it matters
        - For architecture: explain the flow of data through each layer
        - For database: explain what each table stores and how tables relate to each other
        - For files: explain the specific purpose, key methods, and dependencies
        - Never invent information not present in the data — if unsure say "likely" or "appears to"
        - Format with clear paragraphs, no excessive bullet points
        - Minimum 4-6 sentences per explanation, more for complex topics
        """;

    public AiExplanationService(
        GeminiProvider geminiProvider,
        OpenAiProvider openAiProvider,
        @Value("${ai.provider:gemini}") String configuredProvider
    ) {
        if ("openai".equalsIgnoreCase(configuredProvider)) {
            this.aiProvider = openAiProvider;
            log.info("Using OpenAI as AI provider");
        } else {
            this.aiProvider = geminiProvider;
            log.info("Using Google Gemini as AI provider (supports both AIza and AQ. key formats)");
        }
    }

    // =================== Project Summary ===================

    public String generateProjectSummary(AnalysisContext context) {
        log.info("Generating project summary...");

        RepositoryInfo repo = context.getRepositoryInfo();
        String readme = context.getReadme() != null
            ? context.getReadme().substring(0, Math.min(800, context.getReadme().length())) : "Not available";

        String frameworks = context.getDetectedFrameworks().stream()
            .map(FrameworkInfo::getName).collect(Collectors.joining(", "));
        String languages = String.join(", ", context.getDetectedLanguages());

        long controllers = countByRole(context, "CONTROLLER");
        long services = countByRole(context, "SERVICE");
        long repos = countByRole(context, "REPOSITORY");
        long entities = countByRole(context, "ENTITY");

        String topClasses = context.getAnalyzedFiles().stream()
            .filter(f -> f.getClasses() != null)
            .flatMap(f -> f.getClasses().stream())
            .filter(c -> c.getSpringRole() != null)
            .map(c -> c.getName() + " [" + c.getSpringRole() + "]")
            .limit(12)
            .collect(Collectors.joining(", "));

        String prompt = """
            Analyze this GitHub repository and write a comprehensive project summary:
            
            Repository: %s/%s
            Description: %s
            Languages: %s
            Frameworks & Libraries: %s
            Database: %s (JPA: %s)
            Architecture: %d Controller(s), %d Service(s), %d Repository(s), %d JPA Entity(s)
            Database Tables: %d detected
            Key classes: %s
            
            README excerpt:
            %s
            
            Write a detailed 5-7 sentence project summary that covers:
            1. What this application does and its main purpose
            2. The technology stack and why these choices make sense
            3. The architecture pattern used (MVC, layered, etc.)
            4. What data the application manages and stores
            5. Who would use this application and for what purpose
            
            Be specific — mention actual class names and technologies found.
            """.formatted(
            context.getOwner(), context.getRepoName(),
            repo != null && repo.getDescription() != null ? repo.getDescription() : "Not provided",
            languages.isEmpty() ? "Not detected" : languages,
            frameworks.isEmpty() ? "Not detected" : frameworks,
            context.getDatabaseInfo() != null ? context.getDatabaseInfo().getTechnology() : "Not detected",
            context.getDatabaseInfo() != null && context.getDatabaseInfo().isUsesJpa() ? "Yes" : "No",
            controllers, services, repos, entities,
            context.getDatabaseTables().size(),
            topClasses.isEmpty() ? "Not detected" : topClasses,
            readme
        );

        return aiProvider.generate(SYSTEM_PROMPT, prompt);
    }

    // =================== Architecture Explanation ===================

    public String generateArchitectureExplanation(AnalysisContext context) {
        log.info("Generating architecture explanation...");

        StringBuilder layers = new StringBuilder();
        List.of("CONTROLLER", "SERVICE", "REPOSITORY", "ENTITY", "CONFIG", "COMPONENT")
            .forEach(role -> {
                List<String> classes = context.getAnalyzedFiles().stream()
                    .filter(f -> f.getClasses() != null)
                    .flatMap(f -> f.getClasses().stream())
                    .filter(c -> role.equals(c.getSpringRole()))
                    .map(ClassInfo::getName)
                    .collect(Collectors.toList());
                if (!classes.isEmpty())
                    layers.append(role).append(": ").append(String.join(", ", classes)).append("\n");
            });

        // Collect all detected endpoints
        String endpoints = context.getAnalyzedFiles().stream()
            .filter(f -> f.getEndpoints() != null)
            .flatMap(f -> f.getEndpoints().stream())
            .map(e -> e.getHttpMethod() + " " + e.getPath())
            .limit(15)
            .collect(Collectors.joining(", "));

        String prompt = """
            Explain the software architecture of this application in detail:
            
            Project: %s/%s
            Frameworks: %s
            
            Architectural Layers detected:
            %s
            
            REST API Endpoints detected: %s
            
            Database: %s with %d tables
            
            Write a detailed architecture explanation (6-8 sentences) covering:
            1. The overall architectural pattern (e.g., MVC, layered, hexagonal)
            2. How the Controller layer handles incoming HTTP requests and routes them
            3. What business logic the Service layer contains and how it coordinates work
            4. How the Repository layer abstracts database access using Spring Data JPA
            5. How Entity classes map to database tables via Hibernate
            6. The complete flow of a typical request from HTTP call to database and back
            7. Any notable design patterns observed (Dependency Injection, Repository pattern, etc.)
            
            Be specific with actual class names from the data above.
            """.formatted(
            context.getOwner(), context.getRepoName(),
            context.getDetectedFrameworks().stream().map(FrameworkInfo::getName).collect(Collectors.joining(", ")),
            layers,
            endpoints.isEmpty() ? "Not detected" : endpoints,
            context.getDatabaseInfo() != null ? context.getDatabaseInfo().getTechnology() : "Not detected",
            context.getDatabaseTables().size()
        );

        return aiProvider.generate(SYSTEM_PROMPT, prompt);
    }

    // =================== Database Explanation ===================

    public String generateDatabaseExplanation(AnalysisContext context) {
        log.info("Generating database explanation...");

        StringBuilder tableDetails = new StringBuilder();
        for (TableInfo table : context.getDatabaseTables()) {
            tableDetails.append("\nTable: ").append(table.getTableName());
            if (table.getEntityClass() != null)
                tableDetails.append(" (JPA entity: ").append(table.getEntityClass()).append(")");
            tableDetails.append("\n");
            if (table.getColumns() != null) {
                table.getColumns().forEach(col -> {
                    tableDetails.append("  - ").append(col.getColumnName())
                        .append(" ").append(col.getDataType());
                    if (col.isPrimaryKey()) tableDetails.append(" [PRIMARY KEY]");
                    if (col.isForeignKey()) tableDetails.append(" [FK -> ").append(col.getReferencedTable()).append("]");
                    if (!col.isNullable() && !col.isPrimaryKey()) tableDetails.append(" NOT NULL");
                    tableDetails.append("\n");
                });
            }
        }

        String prompt = """
            Explain the database design of this application in detail:
            
            Project: %s/%s
            Database Technology: %s
            Detection source: %s
            Uses JPA/Hibernate: %s
            
            Tables and Columns:
            %s
            
            Write a detailed database explanation (6-8 sentences) covering:
            1. What database technology is used and why it suits this application
            2. What each table stores and its business purpose
            3. The relationships between tables (which tables reference which)
            4. The primary keys and foreign key relationships
            5. How JPA entity classes map to these database tables
            6. The overall data model and how it supports the application's features
            7. Any observations about the database design quality or patterns used
            
            Be specific — mention actual table names, column names, and entity class names.
            """.formatted(
            context.getOwner(), context.getRepoName(),
            context.getDatabaseInfo() != null ? context.getDatabaseInfo().getTechnology() : "Not detected",
            context.getDatabaseInfo() != null ? context.getDatabaseInfo().getDetectionMethod() : "Unknown",
            context.getDatabaseInfo() != null && context.getDatabaseInfo().isUsesJpa() ? "Yes" : "No",
            tableDetails
        );

        return aiProvider.generate(SYSTEM_PROMPT, prompt);
    }

    // =================== How It Works ===================

    public String generateHowItWorks(AnalysisContext context) {
        log.info("Generating how-it-works explanation...");

        String endpoints = context.getAnalyzedFiles().stream()
            .filter(f -> f.getEndpoints() != null)
            .flatMap(f -> f.getEndpoints().stream())
            .map(e -> e.getHttpMethod() + " " + e.getPath() + " (in " +
                context.getAnalyzedFiles().stream()
                    .filter(f -> f.getEndpoints() != null && f.getEndpoints().contains(e))
                    .map(AnalyzedFileDto::getFileName).findFirst().orElse("?") + ")")
            .limit(10)
            .collect(Collectors.joining("\n  "));

        long controllers = countByRole(context, "CONTROLLER");
        long services = countByRole(context, "SERVICE");
        long entities = countByRole(context, "ENTITY");

        String prompt = """
            Explain how this application works end-to-end:
            
            Project: %s/%s
            Type: %s
            Frameworks: %s
            Database: %s
            Architecture: %d controllers, %d services, %d JPA entities, %d database tables
            
            Detected API endpoints:
              %s
            
            Write a detailed "How It Works" explanation (7-9 sentences) covering:
            1. What the application does from the user's perspective
            2. How a user or client interacts with the application (REST API, web UI, etc.)
            3. The step-by-step flow when a request comes in: Controller → Service → Repository → Database
            4. How data is validated, processed, and transformed at each layer
            5. How the database stores and retrieves data
            6. Any authentication or security mechanisms in place
            7. How the application starts up (Spring Boot auto-configuration)
            8. The overall data lifecycle from input to storage to response
            
            Make it easy for a beginner to understand the complete picture.
            """.formatted(
            context.getOwner(), context.getRepoName(),
            context.getProjectType() != null ? context.getProjectType() : "Spring Boot Application",
            context.getDetectedFrameworks().stream().map(FrameworkInfo::getName).collect(Collectors.joining(", ")),
            context.getDatabaseInfo() != null ? context.getDatabaseInfo().getTechnology() : "Not detected",
            controllers, services, entities, context.getDatabaseTables().size(),
            endpoints.isEmpty() ? "No endpoints detected" : endpoints
        );

        return aiProvider.generate(SYSTEM_PROMPT, prompt);
    }

    // =================== File Explanation ===================

    public String generateFileExplanation(AnalyzedFileDto file, String repoName) {
        String classes = file.getClasses() != null
            ? file.getClasses().stream().map(c -> {
                String info = c.getName() + " [" + c.getType() + "]";
                if (c.getSpringRole() != null) info += " @" + c.getSpringRole();
                if (c.getMethods() != null) info += " — " + c.getMethods().size() + " methods";
                return info;
            }).collect(Collectors.joining(", ")) : "None";

        String endpoints = file.getEndpoints() != null
            ? file.getEndpoints().stream()
                .map(e -> e.getHttpMethod() + " " + e.getPath())
                .collect(Collectors.joining(", ")) : "None";

        String imports = file.getImports() != null
            ? file.getImports().stream().limit(8).collect(Collectors.joining(", ")) : "None";

        String methods = file.getClasses() != null
            ? file.getClasses().stream()
                .filter(c -> c.getMethods() != null)
                .flatMap(c -> c.getMethods().stream())
                .limit(8)
                .collect(Collectors.joining("; ")) : "None";

        String prompt = """
            Explain this Java source file in detail:
            
            Repository: %s
            File: %s
            Path: %s
            Role: %s
            Purpose: %s
            
            Classes/Types found:
            %s
            
            Methods: %s
            
            REST Endpoints: %s
            
            Key imports: %s
            
            Write a detailed explanation (5-7 sentences) covering:
            1. The exact purpose and responsibility of this file in the application
            2. What each class does and why it exists
            3. The most important methods and what they do
            4. What other classes/files this depends on and why
            5. How this file fits into the overall application architecture
            6. What would break if this file was removed or changed
            
            Be specific — use the actual class names, method names, and endpoint paths shown above.
            """.formatted(
            repoName, file.getFileName(), file.getFilePath(),
            file.getFileType() != null ? file.getFileType() : "JAVA_SOURCE",
            file.getPurpose() != null ? file.getPurpose() : "Not determined",
            classes, methods, endpoints, imports
        );

        return aiProvider.generate(SYSTEM_PROMPT, prompt);
    }

    // =================== AI Chat ===================

    public String answerQuestion(AnalysisContext context, String question) {
        log.info("Answering chat question: {}", question);

        StringBuilder ctx = new StringBuilder();
        ctx.append("=== REPOSITORY CONTEXT ===\n");
        ctx.append("Repository: ").append(context.getOwner()).append("/").append(context.getRepoName()).append("\n");

        if (context.getRepositoryInfo() != null && context.getRepositoryInfo().getDescription() != null)
            ctx.append("Description: ").append(context.getRepositoryInfo().getDescription()).append("\n");

        ctx.append("Languages: ").append(String.join(", ", context.getDetectedLanguages())).append("\n");
        ctx.append("Frameworks: ").append(
            context.getDetectedFrameworks().stream().map(FrameworkInfo::getName)
                .collect(Collectors.joining(", "))).append("\n");
        ctx.append("Database: ").append(
            context.getDatabaseInfo() != null ? context.getDatabaseInfo().getTechnology() : "Not detected").append("\n\n");

        // All classes with roles
        ctx.append("=== ALL CLASSES ===\n");
        context.getAnalyzedFiles().stream()
            .filter(f -> f.getClasses() != null)
            .forEach(f -> f.getClasses().forEach(c -> {
                ctx.append("• ").append(c.getName());
                if (c.getSpringRole() != null) ctx.append(" [").append(c.getSpringRole()).append("]");
                if (c.getPackageName() != null) ctx.append(" in package ").append(c.getPackageName());
                ctx.append(" — file: ").append(f.getFilePath()).append("\n");
                if (c.getMethods() != null && !c.getMethods().isEmpty())
                    ctx.append("  Methods: ").append(c.getMethods().stream().limit(5).collect(Collectors.joining("; "))).append("\n");
            }));

        // All endpoints
        ctx.append("\n=== REST API ENDPOINTS ===\n");
        context.getAnalyzedFiles().stream()
            .filter(f -> f.getEndpoints() != null)
            .forEach(f -> f.getEndpoints().forEach(ep ->
                ctx.append("• ").append(ep.getHttpMethod()).append(" ").append(ep.getPath())
                    .append(" → ").append(ep.getMethodName())
                    .append(" (in ").append(f.getFileName()).append(")\n")));

        // Database tables
        ctx.append("\n=== DATABASE TABLES ===\n");
        context.getDatabaseTables().forEach(t -> {
            ctx.append("• Table: ").append(t.getTableName());
            if (t.getEntityClass() != null) ctx.append(" ↔ entity: ").append(t.getEntityClass());
            ctx.append("\n");
            if (t.getColumns() != null)
                t.getColumns().forEach(col -> ctx.append("  - ")
                    .append(col.getColumnName()).append(" ").append(col.getDataType())
                    .append(col.isPrimaryKey() ? " PK" : "")
                    .append(col.isForeignKey() ? " FK→" + col.getReferencedTable() : "")
                    .append("\n"));
        });

        // Dependencies
        ctx.append("\n=== DEPENDENCIES ===\n");
        context.getDetectedDependencies().stream().limit(20).forEach(dep ->
            ctx.append("• ").append(dep.getGroupId()).append(":").append(dep.getArtifactId())
                .append(":").append(dep.getVersion()).append("\n"));

        String prompt = ctx + "\n=== USER QUESTION ===\n" + question + "\n\n" +
            """
            Answer this question with maximum detail and technical accuracy.
            Rules:
            - Reference actual class names, file names, method names, and table names from the context above
            - Explain the complete flow if the question is about a feature or process
            - If asking about a specific class or file, describe all its methods and dependencies
            - If asking about the database, describe the exact tables, columns, and relationships
            - If the answer requires tracing code flow, do it step by step
            - Minimum 5-8 sentences, more if the question is complex
            - If something cannot be determined from the context, say so clearly
            """;

        return aiProvider.generate(SYSTEM_PROMPT, prompt);
    }

    // =================== Helpers ===================

    private long countByRole(AnalysisContext context, String role) {
        return context.getAnalyzedFiles().stream()
            .filter(f -> f.getClasses() != null)
            .flatMap(f -> f.getClasses().stream())
            .filter(c -> role.equals(c.getSpringRole()))
            .count();
    }
}
