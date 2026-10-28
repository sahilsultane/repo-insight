package com.githubrepoinsight.analyzer;

import com.githubrepoinsight.dto.AnalysisResultDto.DependencyInfo;
import com.githubrepoinsight.dto.AnalysisResultDto.FrameworkInfo;
import com.githubrepoinsight.github.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes pom.xml and build.gradle to extract dependencies and frameworks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BuildSystemAnalyzer {

    private final GitHubService gitHubService;

    private static final Pattern POM_DEPENDENCY_PATTERN = Pattern.compile(
        "<dependency>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>(?:\\s*<version>([^<]+)</version>)?(?:\\s*<scope>([^<]+)</scope>)?",
        Pattern.DOTALL
    );

    private static final Pattern POM_PARENT_PATTERN = Pattern.compile(
        "<parent>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>\\s*<version>([^<]+)</version>",
        Pattern.DOTALL
    );

    public void analyze(AnalysisContext context) {
        log.info("Analyzing build system...");

        // Check for Maven
        if (context.hasFile("pom.xml")) {
            context.setBuildSystem("MAVEN");
            analyzePom(context);
        }
        // Check for Gradle
        else if (context.hasFile("build.gradle") || context.hasFile("build.gradle.kts")) {
            context.setBuildSystem("GRADLE");
            analyzeGradle(context);
        } else {
            context.setBuildSystem("NONE");
        }
    }

    private void analyzePom(AnalysisContext context) {
        String pomPath = context.getTreeEntries().stream()
            .filter(e -> e.getPath().equals("pom.xml") || e.getPath().endsWith("/pom.xml"))
            .findFirst()
            .map(e -> e.getPath())
            .orElse("pom.xml");

        String content = gitHubService.fetchFileContent(
            context.getOwner(), context.getRepoName(), pomPath, context.getBranch());

        if (content == null) {
            log.warn("Could not fetch pom.xml");
            return;
        }

        context.setPomContent(content);
        List<DependencyInfo> deps = new ArrayList<>();

        // Check for Spring Boot parent
        Matcher parentMatcher = POM_PARENT_PATTERN.matcher(content);
        if (parentMatcher.find()) {
            String parentGroupId = parentMatcher.group(1).trim();
            String parentArtifactId = parentMatcher.group(2).trim();
            String parentVersion = parentMatcher.group(3).trim();

            if ("org.springframework.boot".equals(parentGroupId)) {
                context.setProjectType("SPRING_BOOT");
                context.getDetectedFrameworks().add(FrameworkInfo.builder()
                    .name("Spring Boot")
                    .version(parentVersion)
                    .detectionSource("pom.xml (parent)")
                    .category("WEB")
                    .build());
            }
        }

        // Parse dependencies
        Matcher depMatcher = POM_DEPENDENCY_PATTERN.matcher(content);
        while (depMatcher.find()) {
            String groupId = depMatcher.group(1).trim();
            String artifactId = depMatcher.group(2).trim();
            String version = depMatcher.group(3) != null ? depMatcher.group(3).trim() : "managed";
            String scope = depMatcher.group(4) != null ? depMatcher.group(4).trim() : "compile";

            deps.add(DependencyInfo.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .scope(scope)
                .build());

            // Detect well-known frameworks from dependencies
            detectFrameworkFromDependency(context, groupId, artifactId, version, "pom.xml");
        }

        context.getDetectedDependencies().addAll(deps);
        log.info("Found {} dependencies in pom.xml", deps.size());
    }

    private void analyzeGradle(AnalysisContext context) {
        String gradlePath = context.getTreeEntries().stream()
            .filter(e -> e.getPath().equals("build.gradle") || e.getPath().equals("build.gradle.kts"))
            .findFirst()
            .map(e -> e.getPath())
            .orElse("build.gradle");

        String content = gitHubService.fetchFileContent(
            context.getOwner(), context.getRepoName(), gradlePath, context.getBranch());

        if (content == null) {
            log.warn("Could not fetch build.gradle");
            return;
        }

        context.setGradleContent(content);

        if (content.contains("org.springframework.boot")) {
            context.setProjectType("SPRING_BOOT");
            // Try to extract version
            Pattern versionPattern = Pattern.compile("id ['\"]org\\.springframework\\.boot['\"] version ['\"]([^'\"]+)['\"]");
            Matcher m = versionPattern.matcher(content);
            String version = m.find() ? m.group(1) : "detected";
            context.getDetectedFrameworks().add(FrameworkInfo.builder()
                .name("Spring Boot")
                .version(version)
                .detectionSource("build.gradle")
                .category("WEB")
                .build());
        }

        // Parse gradle dependencies (simplified pattern)
        Pattern depPattern = Pattern.compile("(implementation|api|compile|runtimeOnly|testImplementation)\\s+['\"]([^:]+):([^:]+):?([^'\"]*)['\"]");
        Matcher depMatcher = depPattern.matcher(content);
        while (depMatcher.find()) {
            String scope = depMatcher.group(1);
            String groupId = depMatcher.group(2);
            String artifactId = depMatcher.group(3);
            String version = depMatcher.group(4).isEmpty() ? "managed" : depMatcher.group(4);

            context.getDetectedDependencies().add(DependencyInfo.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .scope(scope)
                .build());

            detectFrameworkFromDependency(context, groupId, artifactId, version, "build.gradle");
        }
    }

    private void detectFrameworkFromDependency(AnalysisContext ctx, String groupId, String artifactId, String version, String source) {
        // Avoid duplicates
        String key = groupId + ":" + artifactId;

        if (artifactId.contains("spring-boot-starter-web") || artifactId.contains("spring-webmvc")) {
            addFrameworkIfAbsent(ctx, "Spring MVC", version, source, "WEB");
        } else if (artifactId.contains("spring-boot-starter-data-jpa") || artifactId.contains("spring-data-jpa")) {
            addFrameworkIfAbsent(ctx, "Spring Data JPA", version, source, "ORM");
            ctx.setProjectType(ctx.getProjectType() != null ? ctx.getProjectType() : "SPRING_BOOT");
        } else if (artifactId.contains("spring-boot-starter-security") || artifactId.contains("spring-security")) {
            addFrameworkIfAbsent(ctx, "Spring Security", version, source, "SECURITY");
        } else if (artifactId.contains("hibernate")) {
            addFrameworkIfAbsent(ctx, "Hibernate", version, source, "ORM");
        } else if (artifactId.contains("mysql-connector")) {
            addFrameworkIfAbsent(ctx, "MySQL", version, source, "DATABASE");
        } else if (groupId.contains("postgresql") || artifactId.contains("postgresql")) {
            addFrameworkIfAbsent(ctx, "PostgreSQL", version, source, "DATABASE");
        } else if (artifactId.contains("h2")) {
            addFrameworkIfAbsent(ctx, "H2 Database", version, source, "DATABASE");
        } else if (artifactId.contains("liquibase")) {
            addFrameworkIfAbsent(ctx, "Liquibase", version, source, "MIGRATION");
        } else if (artifactId.contains("flyway")) {
            addFrameworkIfAbsent(ctx, "Flyway", version, source, "MIGRATION");
        } else if (artifactId.contains("lombok")) {
            addFrameworkIfAbsent(ctx, "Lombok", version, source, "UTILITY");
        } else if (artifactId.contains("jjwt") || artifactId.contains("java-jwt")) {
            addFrameworkIfAbsent(ctx, "JWT", version, source, "SECURITY");
        } else if (artifactId.contains("mapstruct")) {
            addFrameworkIfAbsent(ctx, "MapStruct", version, source, "UTILITY");
        } else if (artifactId.contains("swagger") || artifactId.contains("springdoc")) {
            addFrameworkIfAbsent(ctx, "Swagger/OpenAPI", version, source, "DOCUMENTATION");
        } else if (artifactId.contains("spring-boot-starter-test") || artifactId.contains("junit")) {
            addFrameworkIfAbsent(ctx, "JUnit", version, source, "TESTING");
        } else if (artifactId.contains("mockito")) {
            addFrameworkIfAbsent(ctx, "Mockito", version, source, "TESTING");
        } else if (artifactId.contains("redis")) {
            addFrameworkIfAbsent(ctx, "Redis", version, source, "CACHE");
        } else if (artifactId.contains("kafka")) {
            addFrameworkIfAbsent(ctx, "Apache Kafka", version, source, "MESSAGING");
        } else if (artifactId.contains("rabbitmq") || artifactId.contains("amqp")) {
            addFrameworkIfAbsent(ctx, "RabbitMQ", version, source, "MESSAGING");
        } else if (artifactId.contains("thymeleaf")) {
            addFrameworkIfAbsent(ctx, "Thymeleaf", version, source, "TEMPLATE");
        }
    }

    private void addFrameworkIfAbsent(AnalysisContext ctx, String name, String version, String source, String category) {
        boolean exists = ctx.getDetectedFrameworks().stream()
            .anyMatch(f -> f.getName().equals(name));
        if (!exists) {
            ctx.getDetectedFrameworks().add(FrameworkInfo.builder()
                .name(name)
                .version(version)
                .detectionSource(source)
                .category(category)
                .build());
        }
    }
}
