package com.githubrepoinsight.analyzer;

import com.githubrepoinsight.dto.AnalysisResultDto.FileTreeNode;
import com.githubrepoinsight.github.GitHubTreeEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds a hierarchical file tree from the flat GitHub tree entries list.
 */
@Component
@Slf4j
public class FileTreeBuilder {

    private static final Set<String> IGNORE_PATHS = Set.of(
        ".git", "node_modules", "target", "build", "dist",
        ".idea", ".vscode", ".mvn", "__pycache__", ".pytest_cache"
    );

    public FileTreeNode build(AnalysisContext context) {
        Set<String> keyFilePaths = new HashSet<>();
        context.getKeyFiles().forEach(kf -> keyFilePaths.add(kf.getFilePath()));

        // Build map: path -> node
        Map<String, FileTreeNode> nodeMap = new LinkedHashMap<>();
        FileTreeNode root = FileTreeNode.builder()
            .name(context.getRepoName())
            .path("")
            .type("directory")
            .children(new ArrayList<>())
            .build();

        for (GitHubTreeEntry entry : context.getTreeEntries()) {
            if (shouldIgnore(entry.getPath())) continue;

            String[] parts = entry.getPath().split("/");
            String currentPath = "";

            for (int i = 0; i < parts.length; i++) {
                String parentPath = currentPath;
                currentPath = currentPath.isEmpty() ? parts[i] : currentPath + "/" + parts[i];

                if (nodeMap.containsKey(currentPath)) continue;

                boolean isFile = i == parts.length - 1 && "blob".equals(entry.getType());
                String language = isFile ? LanguageDetector.detectLanguageFromPath(currentPath) : null;

                FileTreeNode node = FileTreeNode.builder()
                    .name(parts[i])
                    .path(currentPath)
                    .type(isFile ? "file" : "directory")
                    .language(language)
                    .isKeyFile(keyFilePaths.contains(currentPath))
                    .children(isFile ? null : new ArrayList<>())
                    .build();

                nodeMap.put(currentPath, node);

                // Attach to parent
                FileTreeNode parent = parentPath.isEmpty() ? root : nodeMap.get(parentPath);
                if (parent != null && parent.getChildren() != null) {
                    parent.getChildren().add(node);
                }
            }
        }

        return root;
    }

    private boolean shouldIgnore(String path) {
        String[] parts = path.split("/");
        for (String part : parts) {
            if (IGNORE_PATHS.contains(part)) return true;
        }
        // Skip binary files
        return path.endsWith(".class") || path.endsWith(".jar") || path.endsWith(".war")
            || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".gif")
            || path.endsWith(".pdf") || path.endsWith(".zip") || path.endsWith(".tar.gz");
    }
}
