package com.githubrepoinsight.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "database_columns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String columnName;

    private String dataType;
    private boolean isPrimaryKey;
    private boolean isForeignKey;
    private boolean isNullable;
    private boolean isUnique;
    private String referencedTable;
    private String referencedColumn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private DatabaseTable databaseTable;
}
