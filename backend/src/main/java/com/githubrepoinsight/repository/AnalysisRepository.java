package com.githubrepoinsight.repository;

import com.githubrepoinsight.model.Analysis;
import com.githubrepoinsight.model.Analysis.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    Optional<Analysis> findByAnalysisId(String analysisId);
    List<Analysis> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);
    List<Analysis> findByStatus(AnalysisStatus status);
    Optional<Analysis> findTopByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);
}
