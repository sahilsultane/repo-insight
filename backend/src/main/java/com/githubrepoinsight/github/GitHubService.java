package com.githubrepoinsight.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubrepoinsight.dto.AnalysisResultDto.RepositoryInfo;
import com.githubrepoinsight.exception.GitHubApiException;
import com.githubrepoinsight.exception.RepositoryNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${github.token:}")
    private String githubToken;

    @Value("${github.api.base-url}")
    private String apiBaseUrl;

    private static final Pattern GITHUB_URL_PATTERN =
        Pattern.compile("^https://github\\.com/([a-zA-Z0-9._-]+)/([a-zA-Z0-9._-]+?)(?:\\.git)?(?:/.*)?$");

    /**
     * Validates and parses a GitHub URL, returning [owner, repo].
     */
    public String[] parseGitHubUrl(String url) {
        Matcher matcher = GITHUB_URL_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid GitHub URL. Expected format: https://github.com/owner/repository");
        }
        return new String[]{matcher.group(1), matcher.group(2)};
    }

    /**
     * Fetches repository metadata from the GitHub API.
     */
    public RepositoryInfo fetchRepositoryInfo(String owner, String repo) {
        log.info("Fetching repository metadata for {}/{}", owner, repo);
        String url = apiBaseUrl + "/repos/" + owner + "/" + repo;

        try {
            String response = buildWebClient()
                .get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                    clientResponse.bodyToMono(String.class).flatMap(body -> {
                        if (clientResponse.statusCode().value() == 404) {
                            return Mono.error(new RepositoryNotFoundException(
                                "Repository " + owner + "/" + repo + " not found or is private."));
                        }
                        if (clientResponse.statusCode().value() == 403) {
                            return Mono.error(new GitHubApiException("GitHub API rate limit exceeded. Please provide a GitHub token."));
                        }
                        return Mono.error(new GitHubApiException("GitHub API error: " + body));
                    }))
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);

            String license = null;
            if (root.has("license") && !root.get("license").isNull()) {
                license = root.get("license").path("spdx_id").asText(null);
            }

            return RepositoryInfo.builder()
                .name(root.path("name").asText())
                .owner(root.path("owner").path("login").asText())
                .fullName(root.path("full_name").asText())
                .description(root.path("description").asText(null))
                .url(root.path("html_url").asText())
                .defaultBranch(root.path("default_branch").asText("main"))
                .primaryLanguage(root.path("language").asText(null))
                .stars(root.path("stargazers_count").asLong(0))
                .forks(root.path("forks_count").asLong(0))
                .openIssues(root.path("open_issues_count").asLong(0))
                .license(license)
                .createdAt(root.path("created_at").asText(null))
                .updatedAt(root.path("updated_at").asText(null))
                .sizeKb(root.path("size").asLong(0))
                .isPrivate(root.path("private").asBoolean(false))
                .build();

        } catch (RepositoryNotFoundException | GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching repository info", e);
            throw new GitHubApiException("Failed to fetch repository information: " + e.getMessage());
        }
    }

    /**
     * Fetches the repository's full file tree (recursive).
     */
    public List<GitHubTreeEntry> fetchRepositoryTree(String owner, String repo, String branch) {
        log.info("Fetching file tree for {}/{} on branch {}", owner, repo, branch);
        String url = apiBaseUrl + "/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";

        try {
            String response = buildWebClient()
                .get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                    clientResponse.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new GitHubApiException("Failed to fetch tree: " + body))))
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            List<GitHubTreeEntry> entries = new ArrayList<>();

            if (root.has("truncated") && root.get("truncated").asBoolean()) {
                log.warn("Repository tree was truncated by GitHub API for {}/{}", owner, repo);
            }

            for (JsonNode node : root.path("tree")) {
                entries.add(GitHubTreeEntry.builder()
                    .path(node.path("path").asText())
                    .type(node.path("type").asText()) // "blob" or "tree"
                    .size(node.path("size").asLong(0))
                    .sha(node.path("sha").asText())
                    .build());
            }

            return entries;
        } catch (GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching repository tree", e);
            throw new GitHubApiException("Failed to fetch repository tree: " + e.getMessage());
        }
    }

    /**
     * Fetches the raw content of a file from GitHub.
     */
    public String fetchFileContent(String owner, String repo, String filePath, String branch) {
        String url = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + filePath;
        log.debug("Fetching file content: {}", url);

        try {
            return buildWebClient()
                .get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                    Mono.error(new GitHubApiException("File not found: " + filePath)))
                .bodyToMono(String.class)
                .block();
        } catch (GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not fetch file {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches the README content of a repository.
     */
    public String fetchReadme(String owner, String repo) {
        String url = apiBaseUrl + "/repos/" + owner + "/" + repo + "/readme";
        try {
            String response = buildWebClient()
                .get()
                .uri(url)
                .header("Accept", "application/vnd.github.raw")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, cr -> Mono.empty())
                .bodyToMono(String.class)
                .block();
            return response;
        } catch (Exception e) {
            log.debug("No README found for {}/{}", owner, repo);
            return null;
        }
    }

    private WebClient buildWebClient() {
        WebClient.Builder builder = webClientBuilder
            .baseUrl(apiBaseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
            .defaultHeader(HttpHeaders.USER_AGENT, "GitHubRepoInsight/1.0");

        if (githubToken != null && !githubToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken);
        }

        return builder.build();
    }
}
