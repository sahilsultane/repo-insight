package com.githubrepoinsight.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repositories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String githubUrl;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String repoName;

    private String description;
    private String defaultBranch;
    private String language;
    private Long stars;
    private Long forks;
    private Long openIssues;
    private String license;
    private String githubCreatedAt;
    private String githubUpdatedAt;
    private Long sizeKb;
    private boolean isPrivate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Analysis> analyses = new ArrayList<>();
}
