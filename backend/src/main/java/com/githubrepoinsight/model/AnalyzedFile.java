package com.githubrepoinsight.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "analyzed_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String filePath;

    private String language;
    private String fileType;
    private boolean isKeyFile;
    private int importanceScore;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(columnDefinition = "TEXT")
    private String aiExplanation;

    // JSON arrays
    @Column(columnDefinition = "TEXT")
    private String classesJson;

    @Column(columnDefinition = "TEXT")
    private String methodsJson;

    @Column(columnDefinition = "TEXT")
    private String importsJson;

    @Column(columnDefinition = "TEXT")
    private String endpointsJson;

    @Column(columnDefinition = "TEXT")
    private String dependenciesJson;

    @Column(columnDefinition = "TEXT")
    private String annotationsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;
}
