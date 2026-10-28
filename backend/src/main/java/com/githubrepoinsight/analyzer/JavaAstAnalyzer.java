package com.githubrepoinsight.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.githubrepoinsight.dto.AnalysisResultDto.*;
import com.githubrepoinsight.github.GitHubService;
import com.githubrepoinsight.github.GitHubTreeEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Uses JavaParser to perform AST-based analysis of Java source files.
 * This is the core analysis engine for Java/Spring Boot repositories.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JavaAstAnalyzer {

    private final GitHubService gitHubService;

    // Spring annotations that indicate architectural roles
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
        "RestController", "Controller", "RequestMapping");
    private static final Set<String> SERVICE_ANNOTATIONS = Set.of("Service");
    private static final Set<String> REPOSITORY_ANNOTATIONS = Set.of("Repository");
    private static final Set<String> ENTITY_ANNOTATIONS = Set.of("Entity", "Table");
    private static final Set<String> CONFIG_ANNOTATIONS = Set.of("Configuration", "SpringBootApplication");
    private static final Set<String> COMPONENT_ANNOTATIONS = Set.of("Component");

    private static final Set<String> HTTP_METHOD_ANNOTATIONS = Set.of(
        "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
        "PatchMapping", "RequestMapping");

    // Files to skip (generated, test, etc.)
    private static final Set<String> SKIP_PATHS = Set.of(
        "/target/", "/build/", "/.mvn/", "/node_modules/");

    public void analyze(AnalysisContext context) {
        log.info("Starting Java AST analysis...");
        List<GitHubTreeEntry> javaFiles = context.filesWithExtension("java");

        // Filter out generated/compiled files
        javaFiles = javaFiles.stream()
            .filter(e -> SKIP_PATHS.stream().noneMatch(skip -> e.getPath().contains(skip)))
            .filter(e -> e.getSize() != null && e.getSize() < 200_000) // skip huge generated files
            .toList();

        log.info("Found {} Java files to analyze", javaFiles.size());
        JavaParser parser = new JavaParser();

        int analyzed = 0;
        for (GitHubTreeEntry entry : javaFiles) {
            if (analyzed >= 80) { // cap to avoid rate limiting
                context.getWarnings().add("Analysis capped at 80 Java files. " + javaFiles.size() + " files found total.");
                break;
            }

            try {
                String content = gitHubService.fetchFileContent(
                    context.getOwner(), context.getRepoName(), entry.getPath(), context.getBranch());

                if (content == null || content.isBlank()) continue;

                context.getFileContents().put(entry.getPath(), content);
                AnalyzedFileDto fileDto = parseJavaFile(parser, entry.getPath(), content);
                context.getAnalyzedFiles().add(fileDto);
                analyzed++;

            } catch (Exception e) {
                log.warn("Failed to analyze Java file {}: {}", entry.getPath(), e.getMessage());
            }
        }

        log.info("Java AST analysis complete. Analyzed {} files.", analyzed);
    }

    private AnalyzedFileDto parseJavaFile(JavaParser parser, String filePath, String content) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);

        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return AnalyzedFileDto.builder()
                .fileName(fileName)
                .filePath(filePath)
                .language("Java")
                .fileType("JAVA_SOURCE")
                .purpose("Java source file (parse error or empty)")
                .build();
        }

        CompilationUnit cu = result.getResult().get();
        List<ClassInfo> classes = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        List<EndpointInfo> endpoints = new ArrayList<>();
        List<String> annotations = new ArrayList<>();
        String fileType = "JAVA_SOURCE";
        String springRole = null;

        // Extract imports
        cu.getImports().forEach(imp -> imports.add(imp.getNameAsString()));

        // Analyze each type declaration in this file
        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            ClassInfo classInfo = analyzeTypeDeclaration(typeDecl, cu, endpoints);
            classes.add(classInfo);

            if (classInfo.getSpringRole() != null && springRole == null) {
                springRole = classInfo.getSpringRole();
                fileType = springRole;
            }

            annotations.addAll(classInfo.getAnnotations());
        }

        String purpose = inferFilePurpose(fileName, springRole, classes, annotations);

        return AnalyzedFileDto.builder()
            .fileName(fileName)
            .filePath(filePath)
            .language("Java")
            .fileType(fileType != null ? fileType : "JAVA_SOURCE")
            .purpose(purpose)
            .classes(classes)
            .imports(imports)
            .endpoints(endpoints.isEmpty() ? null : endpoints)
            .annotations(annotations.isEmpty() ? null : annotations.stream().distinct().toList())
            .dependencies(extractDependencies(classes))
            .build();
    }

    private ClassInfo analyzeTypeDeclaration(TypeDeclaration<?> typeDecl, CompilationUnit cu, List<EndpointInfo> endpoints) {
        List<String> classAnnotations = typeDecl.getAnnotations().stream()
            .map(a -> "@" + a.getNameAsString())
            .collect(Collectors.toList());

        String springRole = detectSpringRole(classAnnotations);
        String classType = "CLASS";

        if (typeDecl instanceof ClassOrInterfaceDeclaration cd) {
            if (cd.isInterface()) classType = "INTERFACE";
            else if (cd.isAbstract()) classType = "ABSTRACT";
        } else if (typeDecl instanceof EnumDeclaration) {
            classType = "ENUM";
        }

        List<String> methods = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        List<String> methodEndpoints = new ArrayList<>();

        // Determine base request mapping for controllers
        String baseMapping = "";
        if ("CONTROLLER".equals(springRole)) {
            baseMapping = extractMappingValue(typeDecl.getAnnotations());
        }

        // Analyze members
        for (BodyDeclaration<?> member : typeDecl.getMembers()) {
            if (member instanceof MethodDeclaration md) {
                String methodSig = md.getTypeAsString() + " " + md.getNameAsString() + "(" +
                    md.getParameters().stream()
                        .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
                        .collect(Collectors.joining(", ")) + ")";
                methods.add(methodSig);

                // Detect HTTP endpoints
                for (AnnotationExpr ann : md.getAnnotations()) {
                    String annName = ann.getNameAsString();
                    if (HTTP_METHOD_ANNOTATIONS.contains(annName)) {
                        String path = baseMapping + extractMappingValueFromAnnotation(ann);
                        String httpMethod = annToHttpMethod(annName);
                        endpoints.add(EndpointInfo.builder()
                            .httpMethod(httpMethod)
                            .path(path.isEmpty() ? "/" : path)
                            .methodName(md.getNameAsString())
                            .detectionConfidence("DETECTED")
                            .build());
                    }
                }
            } else if (member instanceof FieldDeclaration fd) {
                fd.getVariables().forEach(v -> {
                    String fieldStr = fd.getElementType().asString() + " " + v.getNameAsString();
                    fields.add(fieldStr);
                });
            }
        }

        String packageName = cu.getPackageDeclaration()
            .map(p -> p.getNameAsString())
            .orElse("(default package)");

        // Extends
        String extendsClass = null;
        List<String> implementsInterfaces = new ArrayList<>();
        if (typeDecl instanceof ClassOrInterfaceDeclaration cd) {
            if (!cd.getExtendedTypes().isEmpty()) {
                extendsClass = cd.getExtendedTypes().get(0).getNameAsString();
            }
            cd.getImplementedTypes().forEach(t -> implementsInterfaces.add(t.getNameAsString()));
        }

        return ClassInfo.builder()
            .name(typeDecl.getNameAsString())
            .type(classType)
            .packageName(packageName)
            .annotations(classAnnotations)
            .methods(methods.isEmpty() ? null : methods)
            .fields(fields.isEmpty() ? null : fields)
            .implementsInterfaces(implementsInterfaces.isEmpty() ? null : implementsInterfaces)
            .extendsClass(extendsClass)
            .springRole(springRole)
            .build();
    }

    private String detectSpringRole(List<String> annotations) {
        for (String ann : annotations) {
            String name = ann.startsWith("@") ? ann.substring(1) : ann;
            if (CONTROLLER_ANNOTATIONS.contains(name)) return "CONTROLLER";
            if (SERVICE_ANNOTATIONS.contains(name)) return "SERVICE";
            if (REPOSITORY_ANNOTATIONS.contains(name)) return "REPOSITORY";
            if (ENTITY_ANNOTATIONS.contains(name)) return "ENTITY";
            if (CONFIG_ANNOTATIONS.contains(name)) return "CONFIG";
            if (COMPONENT_ANNOTATIONS.contains(name)) return "COMPONENT";
        }
        return null;
    }

    private String extractMappingValue(List<AnnotationExpr> annotations) {
        for (AnnotationExpr ann : annotations) {
            if ("RequestMapping".equals(ann.getNameAsString())) {
                return extractMappingValueFromAnnotation(ann);
            }
        }
        return "";
    }

    private String extractMappingValueFromAnnotation(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr sma) {
            String val = sma.getMemberValue().toString();
            return val.replace("\"", "").replace("{", "").replace("}", "").trim();
        } else if (ann instanceof NormalAnnotationExpr nma) {
            for (MemberValuePair pair : nma.getPairs()) {
                if ("value".equals(pair.getNameAsString()) || "path".equals(pair.getNameAsString())) {
                    return pair.getValue().toString().replace("\"", "").trim();
                }
            }
        }
        return "";
    }

    private String annToHttpMethod(String annotation) {
        return switch (annotation) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            default -> "REQUEST";
        };
    }

    private List<String> extractDependencies(List<ClassInfo> classes) {
        return classes.stream()
            .flatMap(c -> Optional.ofNullable(c.getFields()).orElse(List.of()).stream())
            .filter(f -> f.contains(" ") && !f.startsWith("String ") && !f.startsWith("int ")
                && !f.startsWith("long ") && !f.startsWith("boolean "))
            .map(f -> f.split(" ")[0]) // Extract type name
            .filter(t -> Character.isUpperCase(t.charAt(0))) // Only non-primitive types
            .distinct()
            .toList();
    }

    private String inferFilePurpose(String fileName, String springRole, List<ClassInfo> classes, List<String> annotations) {
        if (springRole != null) {
            return switch (springRole) {
                case "CONTROLLER" -> "Handles HTTP requests and defines REST API endpoints.";
                case "SERVICE" -> "Contains business logic for the application.";
                case "REPOSITORY" -> "Provides data access operations to the database.";
                case "ENTITY" -> "Represents a database table/entity in the domain model.";
                case "CONFIG" -> "Provides application configuration and bean definitions.";
                case "COMPONENT" -> "A Spring-managed component with a specific utility role.";
                default -> "Java source file.";
            };
        }

        String lower = fileName.toLowerCase();
        if (lower.contains("test")) return "Contains unit or integration tests.";
        if (lower.contains("exception") || lower.contains("error")) return "Custom exception/error handling.";
        if (lower.contains("util") || lower.contains("helper")) return "Utility or helper functions.";
        if (lower.contains("dto") || lower.contains("request") || lower.contains("response")) return "Data Transfer Object (DTO) for API communication.";
        if (lower.contains("mapper")) return "Object mapping between layers (DTO ↔ Entity).";
        if (lower.contains("filter")) return "HTTP request/response filter.";
        if (lower.endsWith("application.java") || lower.endsWith("app.java")) return "Main Spring Boot application entry point.";

        return "Java source file.";
    }
}
