package com.githubrepoinsight.analyzer;

import com.githubrepoinsight.github.GitHubTreeEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Detects programming languages used in the repository by examining file extensions.
 */
@Component
@Slf4j
public class LanguageDetector {

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = new LinkedHashMap<>();

    static {
        EXTENSION_TO_LANGUAGE.put("java", "Java");
        EXTENSION_TO_LANGUAGE.put("kt", "Kotlin");
        EXTENSION_TO_LANGUAGE.put("py", "Python");
        EXTENSION_TO_LANGUAGE.put("js", "JavaScript");
        EXTENSION_TO_LANGUAGE.put("ts", "TypeScript");
        EXTENSION_TO_LANGUAGE.put("tsx", "TypeScript");
        EXTENSION_TO_LANGUAGE.put("jsx", "JavaScript");
        EXTENSION_TO_LANGUAGE.put("go", "Go");
        EXTENSION_TO_LANGUAGE.put("rs", "Rust");
        EXTENSION_TO_LANGUAGE.put("cs", "C#");
        EXTENSION_TO_LANGUAGE.put("cpp", "C++");
        EXTENSION_TO_LANGUAGE.put("c", "C");
        EXTENSION_TO_LANGUAGE.put("rb", "Ruby");
        EXTENSION_TO_LANGUAGE.put("php", "PHP");
        EXTENSION_TO_LANGUAGE.put("swift", "Swift");
        EXTENSION_TO_LANGUAGE.put("scala", "Scala");
        EXTENSION_TO_LANGUAGE.put("groovy", "Groovy");
        EXTENSION_TO_LANGUAGE.put("sql", "SQL");
        EXTENSION_TO_LANGUAGE.put("html", "HTML");
        EXTENSION_TO_LANGUAGE.put("css", "CSS");
        EXTENSION_TO_LANGUAGE.put("scss", "SCSS");
        EXTENSION_TO_LANGUAGE.put("xml", "XML");
        EXTENSION_TO_LANGUAGE.put("yaml", "YAML");
        EXTENSION_TO_LANGUAGE.put("yml", "YAML");
        EXTENSION_TO_LANGUAGE.put("json", "JSON");
        EXTENSION_TO_LANGUAGE.put("sh", "Shell");
        EXTENSION_TO_LANGUAGE.put("bash", "Shell");
        EXTENSION_TO_LANGUAGE.put("dart", "Dart");
    }

    /**
     * Detects languages and updates context with sorted language list.
     */
    public void detect(AnalysisContext context) {
        log.info("Detecting programming languages...");
        Map<String, Integer> languageCounts = new LinkedHashMap<>();

        for (GitHubTreeEntry entry : context.getTreeEntries()) {
            if (!"blob".equals(entry.getType())) continue;
            if (shouldIgnorePath(entry.getPath())) continue;

            String ext = getExtension(entry.getPath());
            if (ext != null) {
                String language = EXTENSION_TO_LANGUAGE.get(ext.toLowerCase());
                if (language != null) {
                    languageCounts.merge(language, 1, Integer::sum);
                }
            }
        }

        // Sort by frequency (most common first)
        List<String> sortedLanguages = languageCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .toList();

        context.getDetectedLanguages().addAll(sortedLanguages);
        log.info("Detected languages: {}", sortedLanguages);
    }

    public static String detectLanguageFromPath(String path) {
        String ext = getExtension(path);
        if (ext == null) return null;
        return EXTENSION_TO_LANGUAGE.get(ext.toLowerCase());
    }

    private static String getExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0 || lastDot == path.length() - 1) return null;
        String ext = path.substring(lastDot + 1);
        // Ignore extensions that are too long (likely not real extensions)
        if (ext.length() > 10) return null;
        return ext;
    }

    private boolean shouldIgnorePath(String path) {
        return path.contains("node_modules/")
            || path.contains("/.git/")
            || path.contains("/target/")
            || path.contains("/build/")
            || path.contains("/dist/")
            || path.contains("/.idea/")
            || path.contains("/.vscode/")
            || path.endsWith(".class")
            || path.endsWith(".jar")
            || path.endsWith(".war")
            || path.endsWith(".png")
            || path.endsWith(".jpg")
            || path.endsWith(".gif")
            || path.endsWith(".pdf")
            || path.endsWith(".zip");
    }
}
