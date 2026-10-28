package com.githubrepoinsight.repository;

import com.githubrepoinsight.model.AnalyzedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyzedFileRepository extends JpaRepository<AnalyzedFile, Long> {
    List<AnalyzedFile> findByAnalysisIdOrderByImportanceScoreDesc(Long analysisId);
    List<AnalyzedFile> findByAnalysisIdAndIsKeyFile(Long analysisId, boolean isKeyFile);
}
