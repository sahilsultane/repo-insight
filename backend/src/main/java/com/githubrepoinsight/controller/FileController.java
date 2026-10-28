package com.githubrepoinsight.controller;

import com.githubrepoinsight.exception.AnalysisException;
import com.githubrepoinsight.github.GitHubService;
import com.githubrepoinsight.model.Analysis;
import com.githubrepoinsight.repository.AnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides on-demand file content fetching from GitHub.
 * Files are fetched fresh from GitHub using the repo + branch stored in the analysis record.
 */
@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final AnalysisRepository analysisRepository;
    private final GitHubService gitHubService;

    // Files that must never be displayed or sent to AI
    private static final Set<String> SENSITIVE_NAMES = Set.of(
        ".env", ".env.local", ".env.production", ".env.development",
        "credentials", "secrets", "private_key", "id_rsa", "id_ed25519"
    );
    private static final Set<String> SENSITIVE_EXTENSIONS = Set.of(
        ".pem", ".p12", ".jks", ".pfx", ".key", ".keystore"
    );

    /**
     * GET /api/analyses/{id}/file-content?path=src/main/java/...
     * Returns the raw source code of a file in the analyzed repository.
     */
    @GetMapping("/{id}/file-content")
    public ResponseEntity<Map<String, Object>> getFileContent(
            @PathVariable String id,
            @RequestParam String path) {

        Analysis analysis = analysisRepository.findByAnalysisId(id)
            .orElseThrow(() -> new AnalysisException("Analysis not found: " + id));

        if (analysis.getStatus() != Analysis.AnalysisStatus.COMPLETED) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Analysis not yet complete"));
        }

        // Sanitize path — no directory traversal
        String sanitized = path.replace("..", "").replaceAll("^/+", "");

        if (isSensitive(sanitized)) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("path", sanitized);
            resp.put("sensitive", true);
            resp.put("content", null);
            resp.put("message", "This file may contain credentials and is not displayed.");
            resp.put("language", detectLanguage(sanitized));
            return ResponseEntity.ok(resp);
        }

        String owner = analysis.getRepository().getOwner();
        String repo  = analysis.getRepository().getRepoName();
        String branch = analysis.getRepository().getDefaultBranch() != null
            ? analysis.getRepository().getDefaultBranch() : "main";

        try {
            String content = gitHubService.fetchFileContent(owner, repo, sanitized, branch);

            if (content == null) {
                return ResponseEntity.status(404)
                    .body(Map.of("error", "File not found on GitHub", "path", sanitized));
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("path", sanitized);
            resp.put("name", sanitized.contains("/") ? sanitized.substring(sanitized.lastIndexOf('/') + 1) : sanitized);
            resp.put("content", content);
            resp.put("language", detectLanguage(sanitized));
            resp.put("size", content.length());
            resp.put("lines", content.split("\n", -1).length);
            resp.put("sensitive", false);
            resp.put("githubUrl", "https://github.com/" + owner + "/" + repo + "/blob/" + branch + "/" + sanitized);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.warn("Failed to fetch file {} for analysis {}: {}", sanitized, id, e.getMessage());
            return ResponseEntity.status(502)
                .body(Map.of(
                    "error", "Could not retrieve file from GitHub",
                    "reason", e.getMessage(),
                    "path", sanitized
                ));
        }
    }

    private boolean isSensitive(String path) {
        String lower = path.toLowerCase();
        String fileName = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
        if (SENSITIVE_NAMES.contains(fileName)) return true;
        if (lower.startsWith(".env") || lower.contains("/.env")) return true;
        for (String ext : SENSITIVE_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private String detectLanguage(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            // Files without extension
            String name = path.toLowerCase();
            if (name.endsWith("dockerfile") || name.equals("dockerfile")) return "dockerfile";
            if (name.endsWith("makefile")) return "makefile";
            return "plaintext";
        }
        return switch (path.substring(dot + 1).toLowerCase()) {
            case "java"       -> "java";
            case "kt", "kts"  -> "kotlin";
            case "py"         -> "python";
            case "js", "jsx"  -> "javascript";
            case "ts", "tsx"  -> "typescript";
            case "go"         -> "go";
            case "rs"         -> "rust";
            case "cs"         -> "csharp";
            case "cpp", "cc", "cxx" -> "cpp";
            case "c", "h"     -> "c";
            case "rb"         -> "ruby";
            case "php"        -> "php";
            case "swift"      -> "swift";
            case "sql", "ddl" -> "sql";
            case "xml"        -> "xml";
            case "html", "htm"-> "html";
            case "css"        -> "css";
            case "scss", "sass"-> "scss";
            case "json"       -> "json";
            case "yaml", "yml"-> "yaml";
            case "md", "mdx"  -> "markdown";
            case "sh", "bash", "zsh" -> "bash";
            case "bat", "cmd" -> "batch";
            case "gradle"     -> "groovy";
            case "toml"       -> "toml";
            case "properties" -> "properties";
            case "dockerfile" -> "dockerfile";
            default           -> "plaintext";
        };
    }
}
