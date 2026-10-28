package com.githubrepoinsight.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analyses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AnalysisStatus status = AnalysisStatus.PENDING;

    @Column(length = 1000)
    private String statusMessage;

    @Column(columnDefinition = "TEXT")
    private String projectSummary;

    @Column(columnDefinition = "TEXT")
    private String architectureExplanation;

    @Column(columnDefinition = "TEXT")
    private String databaseExplanation;

    @Column(columnDefinition = "TEXT")
    private String howItWorksExplanation;

    @Column(columnDefinition = "TEXT")
    private String detectedLanguagesJson;

    @Column(columnDefinition = "TEXT")
    private String detectedFrameworksJson;

    @Column(columnDefinition = "TEXT")
    private String detectedDependenciesJson;

    @Column(columnDefinition = "TEXT")
    private String architectureGraphJson;

    @Column(columnDefinition = "TEXT")
    private String erDiagramJson;

    @Column(columnDefinition = "TEXT")
    private String fullAnalysisJson;

    private String errorMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;

    // Use EAGER so collections are always loaded — avoids LazyInitializationException
    // in async threads which have no active Hibernate session
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Builder.Default
    private List<AnalyzedFile> files = new ArrayList<>();

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Builder.Default
    private List<DatabaseTable> databaseTables = new ArrayList<>();

    public enum AnalysisStatus {
        PENDING, FETCHING, ANALYZING, GENERATING, COMPLETED, FAILED
    }
}
