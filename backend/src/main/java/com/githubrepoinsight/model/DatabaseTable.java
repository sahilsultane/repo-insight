package com.githubrepoinsight.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "database_tables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tableName;

    private String entityClass;
    private String sourceFile;

    @Column(columnDefinition = "TEXT")
    private String detectionMethod; // JPA_ENTITY, SQL_CREATE, INFERRED

    @OneToMany(mappedBy = "databaseTable", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DatabaseColumn> columns = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;
}
