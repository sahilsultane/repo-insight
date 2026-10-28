package com.githubrepoinsight.analyzer;

import com.githubrepoinsight.dto.AnalysisResultDto.*;
import com.githubrepoinsight.github.GitHubService;
import com.githubrepoinsight.github.GitHubTreeEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes all non-Java files and produces AnalyzedFileDto entries
 * so every file can show analysis data in the frontend.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UniversalFileAnalyzer {

    private final GitHubService gitHubService;

    // File extensions that are useful to analyze
    private static final Set<String> ANALYZABLE_EXTENSIONS = Set.of(
        "md", "mdx", "txt", "rst",
        "json", "yaml", "yml", "toml", "ini", "env", "properties",
        "sql", "ddl",
        "html", "htm", "css", "scss", "sass", "less",
        "xml", "svg",
        "sh", "bash", "zsh", "bat", "cmd", "ps1",
        "dockerfile", "gradle", "kts", "groovy",
        "py", "rb", "php", "go", "rs", "cs", "kt", "swift", "dart",
        "ts", "tsx", "js", "jsx",
        "tf", "hcl"   // Terraform
    );

    // Key config/doc filenames regardless of extension
    private static final Set<String> KEY_FILENAMES = Set.of(
        "dockerfile", "makefile", "gemfile", "procfile", "rakefile",
        "readme", "license", "changelog", "contributing",
        "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
        "settings.gradle", "settings.gradle.kts",
        "docker-compose.yml", "docker-compose.yaml",
        "application.properties", "application.yml", "application.yaml",
        "requirements.txt", "pyproject.toml", "setup.py", "setup.cfg",
        "cargo.toml", "go.mod", "composer.json",
        "tsconfig.json", "vite.config.ts", "vite.config.js",
        "next.config.js", "next.config.ts", "webpack.config.js",
        ".env.example", ".env.sample", "schema.sql", "schema.prisma"
    );

    public void analyze(AnalysisContext context) {
        log.info("Running universal file analysis on non-Java files...");

        // Build set of already-analyzed file paths (from Java AST analyzer)
        Set<String> alreadyAnalyzed = new HashSet<>();
        context.getAnalyzedFiles().forEach(f -> alreadyAnalyzed.add(f.getFilePath()));

        // Find files to analyze universally
        List<GitHubTreeEntry> toAnalyze = new ArrayList<>();
        for (GitHubTreeEntry entry : context.getTreeEntries()) {
            if (!"blob".equals(entry.getType())) continue;
            if (alreadyAnalyzed.contains(entry.getPath())) continue;
            if (shouldSkip(entry.getPath())) continue;
            if (isAnalyzable(entry.getPath())) {
                toAnalyze.add(entry);
            }
        }

        log.info("Universal analyzer: {} non-Java files to analyze", toAnalyze.size());

        // Cap to avoid too many GitHub API calls
        int cap = Math.min(toAnalyze.size(), 60);
        for (int i = 0; i < cap; i++) {
            GitHubTreeEntry entry = toAnalyze.get(i);
            try {
                String content = gitHubService.fetchFileContent(
                    context.getOwner(), context.getRepoName(), entry.getPath(), context.getBranch());
                if (content == null || content.isBlank()) continue;

                // Store content for AI use
                context.getFileContents().put(entry.getPath(), content);

                AnalyzedFileDto dto = analyzeFile(entry.getPath(), content, context);
                context.getAnalyzedFiles().add(dto);

            } catch (Exception e) {
                log.debug("Could not analyze {}: {}", entry.getPath(), e.getMessage());
            }
        }

        log.info("Universal analysis complete. Total analyzed files: {}", context.getAnalyzedFiles().size());
    }

    private AnalyzedFileDto analyzeFile(String path, String content, AnalysisContext context) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String ext = getExtension(path).toLowerCase();
        String language = LanguageDetector.detectLanguageFromPath(path);
        String fileType = classifyFileType(path, fileName, ext);

        List<String> imports = new ArrayList<>();
        List<String> exports = new ArrayList<>();
        List<String> dependencies = new ArrayList<>();
        String purpose = inferPurpose(fileName, ext, fileType, content);

        // Language-specific extraction
        switch (ext) {
            case "json"  -> extractFromJson(content, fileName, imports, dependencies, exports);
            case "yaml", "yml" -> extractFromYaml(content, fileName, imports, dependencies);
            case "md", "mdx" -> extractFromMarkdown(content, imports, dependencies);
            case "py"    -> extractFromPython(content, imports, exports);
            case "js", "jsx" -> extractFromJs(content, imports, exports);
            case "ts", "tsx" -> extractFromJs(content, imports, exports); // same patterns
            case "go"    -> extractFromGo(content, imports);
            case "sh", "bash" -> extractFromShell(content, dependencies);
            case "sql", "ddl"  -> extractFromSql(content, dependencies);
            case "gradle", "kts" -> extractFromGradle(content, dependencies);
            case "properties" -> extractFromProperties(content, dependencies);
            case "toml"  -> extractFromToml(content, dependencies);
            default -> {}
        }

        return AnalyzedFileDto.builder()
            .fileName(fileName)
            .filePath(path)
            .language(language != null ? language : capitalizeExt(ext))
            .fileType(fileType)
            .purpose(purpose)
            .imports(imports.isEmpty() ? null : imports)
            .dependencies(dependencies.isEmpty() ? null : dependencies)
            .build();
    }

    // ─── Language-specific extractors ─────────────────────────────────────────

    private void extractFromJson(String content, String fileName, List<String> imports, List<String> deps, List<String> exports) {
        // package.json — extract dependencies
        if (fileName.equalsIgnoreCase("package.json")) {
            Pattern depBlock = Pattern.compile("\"(dependencies|devDependencies|peerDependencies)\"\\s*:\\s*\\{([^}]+)}", Pattern.DOTALL);
            Matcher m = depBlock.matcher(content);
            while (m.find()) {
                Pattern pkgPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher pm = pkgPattern.matcher(m.group(2));
                while (pm.find()) deps.add(pm.group(1) + "@" + pm.group(2));
            }
            // scripts
            Pattern scripts = Pattern.compile("\"scripts\"\\s*:\\s*\\{([^}]+)}", Pattern.DOTALL);
            Matcher sm = scripts.matcher(content);
            if (sm.find()) {
                Pattern scriptEntry = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher em = scriptEntry.matcher(sm.group(1));
                while (em.find()) exports.add("script: " + em.group(1) + " → " + em.group(2));
            }
        }
    }

    private void extractFromYaml(String content, String fileName, List<String> imports, List<String> deps) {
        // docker-compose: extract services
        if (fileName.contains("docker-compose")) {
            Pattern svc = Pattern.compile("^\\s{2}([\\w-]+)\\s*:", Pattern.MULTILINE);
            Matcher m = svc.matcher(content);
            while (m.find()) deps.add("service: " + m.group(1));
        }
        // application.yml: extract spring datasource, active profiles
        if (fileName.startsWith("application")) {
            Pattern ds = Pattern.compile("url:\\s*([^\n]+)");
            Matcher m = ds.matcher(content);
            if (m.find()) deps.add("datasource: " + m.group(1).trim());
        }
    }

    private void extractFromMarkdown(String content, List<String> imports, List<String> deps) {
        // Extract section headers
        Pattern headers = Pattern.compile("^#{1,3}\\s+(.+)$", Pattern.MULTILINE);
        Matcher m = headers.matcher(content);
        while (m.find() && imports.size() < 15) {
            imports.add("## " + m.group(1).trim());
        }
        // Extract links/tech mentions
        Pattern links = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
        Matcher lm = links.matcher(content);
        while (lm.find() && deps.size() < 10) {
            String url = lm.group(2);
            if (!url.startsWith("http") && !url.startsWith("#")) {
                deps.add(lm.group(1));
            }
        }
    }

    private void extractFromPython(String content, List<String> imports, List<String> exports) {
        // import statements
        Pattern imp = Pattern.compile("^(?:import|from)\\s+([\\w.]+)", Pattern.MULTILINE);
        Matcher m = imp.matcher(content);
        while (m.find() && imports.size() < 20) imports.add(m.group(1));
        // def / class
        Pattern defs = Pattern.compile("^(?:def|class|async def)\\s+(\\w+)", Pattern.MULTILINE);
        Matcher dm = defs.matcher(content);
        while (dm.find() && exports.size() < 15) exports.add(dm.group(1) + "()");
    }

    private void extractFromJs(String content, List<String> imports, List<String> exports) {
        // import statements
        Pattern imp = Pattern.compile("(?:import|require)\\s*[({]?[^'\"]*(from\\s+)?['\"]([^'\"]+)['\"]");
        Matcher m = imp.matcher(content);
        while (m.find() && imports.size() < 20) {
            String mod = m.group(2);
            if (!mod.startsWith(".")) imports.add(mod);
            else imports.add(mod + " (local)");
        }
        // export statements
        Pattern exp = Pattern.compile("export\\s+(?:default\\s+)?(?:function|class|const|let|var)?\\s*(\\w+)");
        Matcher em = exp.matcher(content);
        while (em.find() && exports.size() < 10) exports.add(em.group(1));
    }

    private void extractFromGo(String content, List<String> imports) {
        Pattern imp = Pattern.compile("\"([^\"]+)\"");
        // Find import block
        Pattern impBlock = Pattern.compile("import\\s*\\(([^)]+)\\)", Pattern.DOTALL);
        Matcher bm = impBlock.matcher(content);
        if (bm.find()) {
            Matcher m = imp.matcher(bm.group(1));
            while (m.find() && imports.size() < 20) imports.add(m.group(1));
        }
    }

    private void extractFromShell(String content, List<String> deps) {
        // Extract called commands / tools
        Pattern cmd = Pattern.compile("^\\s*(\\w+)\\s+", Pattern.MULTILINE);
        Matcher m = cmd.matcher(content);
        Set<String> seen = new HashSet<>();
        Set<String> IGNORE = Set.of("if", "then", "else", "fi", "for", "do", "done", "echo", "set", "exit");
        while (m.find() && deps.size() < 15) {
            String c = m.group(1);
            if (!IGNORE.contains(c) && !seen.contains(c) && c.length() > 2) {
                seen.add(c); deps.add(c);
            }
        }
    }

    private void extractFromSql(String content, List<String> deps) {
        Pattern tables = Pattern.compile("(?:CREATE TABLE|INSERT INTO|UPDATE|FROM|JOIN)\\s+[`\"']?([\\w]+)[`\"']?", Pattern.CASE_INSENSITIVE);
        Matcher m = tables.matcher(content);
        Set<String> seen = new HashSet<>();
        while (m.find() && deps.size() < 20) {
            String t = m.group(1).toLowerCase();
            if (!seen.contains(t)) { seen.add(t); deps.add("table: " + t); }
        }
    }

    private void extractFromGradle(String content, List<String> deps) {
        Pattern dep = Pattern.compile("(?:implementation|api|compile|runtimeOnly|testImplementation)\\s*['\"]([^'\"]+)['\"]");
        Matcher m = dep.matcher(content);
        while (m.find() && deps.size() < 30) deps.add(m.group(1));
    }

    private void extractFromProperties(String content, List<String> deps) {
        Pattern prop = Pattern.compile("^([\\w.]+)\\s*=\\s*(.+)$", Pattern.MULTILINE);
        Matcher m = prop.matcher(content);
        while (m.find() && deps.size() < 20) {
            String key = m.group(1);
            String val = m.group(2).trim();
            // Redact sensitive values
            if (key.toLowerCase().matches(".*(?:password|secret|key|token|credential).*")) {
                deps.add(key + " = [REDACTED]");
            } else {
                deps.add(key + " = " + val);
            }
        }
    }

    private void extractFromToml(String content, List<String> deps) {
        // Cargo.toml, pyproject.toml
        Pattern dep = Pattern.compile("^([\\w-]+)\\s*=\\s*[\"']?([\\d.^~>=<]+)[\"']?", Pattern.MULTILINE);
        Matcher m = dep.matcher(content);
        while (m.find() && deps.size() < 20) deps.add(m.group(1) + " = " + m.group(2));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String classifyFileType(String path, String fileName, String ext) {
        String lower = fileName.toLowerCase();
        return switch (ext) {
            case "md", "mdx", "txt", "rst" -> "DOCUMENTATION";
            case "json", "yaml", "yml", "toml", "ini", "properties", "env" -> "CONFIG";
            case "sql", "ddl" -> "DATABASE";
            case "html", "htm" -> "WEB";
            case "css", "scss", "sass", "less" -> "WEB";
            case "sh", "bash", "zsh", "bat", "cmd", "ps1" -> "SCRIPT";
            case "py" -> lower.contains("test") ? "TEST" : "SOURCE_CODE";
            case "js", "jsx" -> lower.contains("test") || lower.contains("spec") ? "TEST" : "SOURCE_CODE";
            case "ts", "tsx" -> lower.contains("test") || lower.contains("spec") ? "TEST" : "SOURCE_CODE";
            case "go", "rs", "cs", "kt", "swift", "dart", "rb", "php" ->
                lower.contains("test") ? "TEST" : "SOURCE_CODE";
            case "gradle", "kts" -> "BUILD";
            case "dockerfile" -> "INFRASTRUCTURE";
            case "tf", "hcl" -> "INFRASTRUCTURE";
            default -> {
                if (lower.equals("dockerfile") || lower.contains("docker-compose")) yield "INFRASTRUCTURE";
                if (lower.equals("makefile") || lower.equals("rakefile")) yield "BUILD";
                if (lower.equals("gemfile") || lower.equals("package.json") || lower.equals("pom.xml")) yield "BUILD";
                yield "SOURCE_CODE";
            }
        };
    }

    private String inferPurpose(String fileName, String ext, String fileType, String content) {
        String lower = fileName.toLowerCase();
        // Common filenames
        if (lower.equals("readme.md") || lower.equals("readme")) return "Project documentation — explains the purpose, setup, and usage of this repository.";
        if (lower.equals("package.json")) return "Node.js package manifest — defines dependencies, scripts, and project metadata.";
        if (lower.equals("pom.xml")) return "Maven build file — declares Java dependencies, plugins, and project configuration.";
        if (lower.startsWith("build.gradle")) return "Gradle build file — defines dependencies and build tasks for the project.";
        if (lower.startsWith("docker-compose")) return "Docker Compose file — orchestrates multi-container application services.";
        if (lower.equals("dockerfile")) return "Docker image definition — describes how to containerize and run the application.";
        if (lower.startsWith("application.") && (ext.equals("properties") || ext.equals("yml") || ext.equals("yaml")))
            return "Spring Boot application configuration — database, server, and framework settings.";
        if (lower.equals("schema.sql") || lower.equals("schema.ddl")) return "SQL database schema — defines tables, columns, primary keys, and foreign keys.";
        if (lower.equals("requirements.txt")) return "Python dependency list — specifies required packages and versions.";
        if (lower.equals("go.mod")) return "Go module definition — declares the module path and dependencies.";
        if (lower.equals("cargo.toml")) return "Rust package manifest — defines crate metadata and dependencies.";
        if (lower.equals(".env.example") || lower.equals(".env.sample")) return "Environment variable template — shows required configuration variables without real secrets.";
        if (lower.equals("tsconfig.json")) return "TypeScript compiler configuration — defines compilation options and file inclusion.";
        if (lower.equals("vite.config.ts") || lower.equals("vite.config.js")) return "Vite build tool configuration — defines bundler settings, plugins, and dev server options.";
        if (lower.equals("next.config.js") || lower.equals("next.config.ts")) return "Next.js framework configuration — routing, optimization, and deployment settings.";
        // By type
        return switch (fileType) {
            case "DOCUMENTATION" -> "Documentation file — describes project features, setup, or usage.";
            case "CONFIG"        -> "Configuration file — defines application or environment settings.";
            case "DATABASE"      -> "Database file — defines schema, data, or migrations.";
            case "WEB"           -> "Web frontend file — HTML, CSS, or web resources.";
            case "SCRIPT"        -> "Shell or automation script — runs commands or automates tasks.";
            case "BUILD"         -> "Build configuration — defines how the project is compiled and packaged.";
            case "INFRASTRUCTURE"-> "Infrastructure file — defines deployment, containers, or cloud resources.";
            case "TEST"          -> "Test file — contains automated tests for application code.";
            default              -> "Source file in " + (ext.isEmpty() ? "unknown" : ext.toUpperCase()) + " language.";
        };
    }

    private boolean isAnalyzable(String path) {
        String ext = getExtension(path).toLowerCase();
        String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        if (ANALYZABLE_EXTENSIONS.contains(ext)) return true;
        return KEY_FILENAMES.contains(fileName) || fileName.equals("dockerfile") || fileName.equals("makefile");
    }

    private boolean shouldSkip(String path) {
        return path.startsWith(".git/") || path.contains("/node_modules/")
            || path.contains("/target/") || path.contains("/build/")
            || path.contains("/.idea/") || path.contains("/.vscode/")
            || path.endsWith(".class") || path.endsWith(".jar")
            || path.endsWith(".png") || path.endsWith(".jpg")
            || path.endsWith(".gif") || path.endsWith(".ico")
            || path.endsWith(".pdf") || path.endsWith(".zip")
            || path.endsWith(".woff") || path.endsWith(".woff2")
            || path.endsWith(".ttf") || path.endsWith(".eot");
    }

    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        return path.substring(dot + 1);
    }

    private static String capitalizeExt(String ext) {
        if (ext == null || ext.isEmpty()) return "Text";
        return Character.toUpperCase(ext.charAt(0)) + ext.substring(1).toLowerCase();
    }
}
