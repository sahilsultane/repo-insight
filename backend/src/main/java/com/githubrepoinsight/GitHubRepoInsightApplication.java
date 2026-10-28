package com.githubrepoinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class GitHubRepoInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitHubRepoInsightApplication.class, args);
    }
}
