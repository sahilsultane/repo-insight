package com.githubrepoinsight.repository;

import com.githubrepoinsight.model.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, Long> {
    Optional<Repository> findByGithubUrl(String githubUrl);
    List<Repository> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Repository> findFirstByOwnerAndRepoName(String owner, String repoName);
}
