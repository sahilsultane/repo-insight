package com.githubrepoinsight.dto;

import com.githubrepoinsight.model.Analysis.AnalysisStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultDto {

    private String analysisId;
    private AnalysisStatus status;
    private String statusMessage;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // Repository metadata
    private RepositoryInfo repositoryInfo;

    // AI explanations
    private String projectSummary;
    private String architectureExplanation;
    private String databaseExplanation;
    private String howItWorksExplanation;

    // Technology detection
    private List<String> detectedLanguages;
    private List<FrameworkInfo> detectedFrameworks;
    private List<DependencyInfo> detectedDependencies;

    // Codebase structure
    private FileTreeNode fileTree;
    private List<KeyFileInfo> keyFiles;
    private List<AnalyzedFileDto> analyzedFiles;

    // Database
    private DatabaseInfo databaseInfo;
    private List<TableInfo> databaseTables;
    private List<RelationshipInfo> databaseRelationships;  // dedicated relationship model

    // Visualizations
    private GraphData architectureGraph;
    private GraphData erDiagram;

    // =================== Nested DTOs ===================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepositoryInfo {
        private String name;
        private String owner;
        private String fullName;
        private String description;
        private String url;
        private String defaultBranch;
        private String primaryLanguage;
        private Long stars;
        private Long forks;
        private Long openIssues;
        private String license;
        private String createdAt;
        private String updatedAt;
        private Long sizeKb;
        private boolean isPrivate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrameworkInfo {
        private String name;
        private String version;
        private String detectionSource; // e.g., "pom.xml", "build.gradle"
        private String category; // WEB, ORM, SECURITY, TESTING, etc.
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependencyInfo {
        private String groupId;
        private String artifactId;
        private String version;
        private String scope;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileTreeNode {
        private String name;
        private String path;
        private String type; // "file" or "directory"
        private String language;
        private boolean isKeyFile;
        private List<FileTreeNode> children;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyFileInfo {
        private String fileName;
        private String filePath;
        private String reason;
        private String purpose;
        private String aiExplanation;
        private int importanceScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzedFileDto {
        private String fileName;
        private String filePath;
        private String language;
        private String fileType;
        private String purpose;
        private String aiExplanation;
        private List<ClassInfo> classes;
        private List<String> imports;
        private List<EndpointInfo> endpoints;
        private List<String> dependencies;
        private List<String> annotations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassInfo {
        private String name;
        private String type; // CLASS, INTERFACE, ENUM, ABSTRACT
        private String packageName;
        private List<String> annotations;
        private List<String> methods;
        private List<String> fields;
        private List<String> implementsInterfaces;
        private String extendsClass;
        private String springRole; // CONTROLLER, SERVICE, REPOSITORY, ENTITY, CONFIG
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointInfo {
        private String httpMethod;
        private String path;
        private String methodName;
        private String detectionConfidence; // DETECTED, INFERRED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseInfo {
        private String technology; // MySQL, PostgreSQL, etc.
        private String detectionMethod; // application.properties, pom.xml, etc.
        private String jdbcUrl;
        private String confidence; // DETECTED, INFERRED
        private boolean usesJpa;
        private boolean usesHibernate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableInfo {
        private String tableName;
        private String entityClass;
        private String sourceFile;
        private String detectionMethod;
        private List<ColumnInfo> columns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        private String columnName;
        private String dataType;
        private boolean isPrimaryKey;
        private boolean isForeignKey;
        private boolean isNullable;
        private boolean isUnique;
        private String referencedTable;
        private String referencedColumn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphData {
        private List<GraphNode> nodes;
        private List<GraphEdge> edges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNode {
        private String id;
        private String label;
        private String type;
        private Map<String, Object> data;
        private Map<String, Object> position;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge {
        private String id;
        private String source;
        private String target;
        private String label;
        private String type;
        private String sourceHandle;   // connects to specific column handle
        private String targetHandle;   // connects to specific column handle
        private String cardinality;    // "1:N", "N:1", "1:1", "N:M"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationshipInfo {
        private String id;
        private String sourceTable;
        private String sourceColumn;
        private String targetTable;
        private String targetColumn;
        private String relationshipType;  // MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE, MANY_TO_MANY
        private String cardinality;       // "N:1", "1:N", "1:1", "N:M"
        private String evidence;          // human-readable source evidence
        private String evidenceSource;    // "SQL_FK", "JPA_MANY_TO_ONE", "JPA_JOIN_COLUMN", "INFERRED"
        private String sourceFile;
        private double confidence;        // 0.0 - 1.0
    }
}
