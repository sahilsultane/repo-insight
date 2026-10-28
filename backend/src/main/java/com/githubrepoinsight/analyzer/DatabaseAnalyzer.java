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
 * Detects database technology, parses JPA entities and SQL scripts.
 * Correctly identifies inline PRIMARY KEY, table-level PRIMARY KEY,
 * table-level FOREIGN KEY, inline REFERENCES, and ALTER TABLE FK.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseAnalyzer {

    private final GitHubService gitHubService;

    // Matches the body inside CREATE TABLE (...) — handles MySQL engine= suffix
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
        "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"\\[\\]]?([\\w]+)[`\"\\[\\]]?\\s*\\(([\\s\\S]+?)\\)\\s*(?:engine\\s*=|ENGINE\\s*=|;|$)",
        Pattern.CASE_INSENSITIVE);

    // Table-level: FOREIGN KEY (col) REFERENCES table(col)
    private static final Pattern TABLE_FK = Pattern.compile(
        "FOREIGN\\s+KEY\\s*\\([`\"']?([\\w]+)[`\"']?\\)\\s*REFERENCES\\s+[`\"']?([\\w]+)[`\"']?\\s*\\([`\"']?([\\w]+)[`\"']?\\)",
        Pattern.CASE_INSENSITIVE);

    // Table-level: PRIMARY KEY (col1, col2)
    private static final Pattern TABLE_PK = Pattern.compile(
        "^\\s*(?:CONSTRAINT\\s+\\w+\\s+)?PRIMARY\\s+KEY\\s*\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // ALTER TABLE ADD FK
    private static final Pattern ALTER_FK = Pattern.compile(
        "ALTER\\s+TABLE\\s+[`\"']?([\\w]+)[`\"']?\\s+ADD\\s+(?:CONSTRAINT\\s+\\w+\\s+)?FOREIGN\\s+KEY\\s*\\([`\"']?([\\w]+)[`\"']?\\)\\s*REFERENCES\\s+[`\"']?([\\w]+)[`\"']?\\s*\\([`\"']?([\\w]+)[`\"']?\\)",
        Pattern.CASE_INSENSITIVE);

    // Inline REFERENCES on a column line: col INT REFERENCES other_table(col)
    private static final Pattern INLINE_REF = Pattern.compile(
        "REFERENCES\\s+[`\"']?([\\w]+)[`\"']?\\s*\\([`\"']?([\\w]+)[`\"']?\\)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern JDBC_URL = Pattern.compile(
        "(?:spring\\.datasource\\.url\\s*=\\s*|url:\\s*)(jdbc:[a-z]+://[^\\s]+)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern JPA_TABLE_NAME = Pattern.compile(
        "@Table\\s*\\([^)]*name\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public void analyze(AnalysisContext context) {
        log.info("Analyzing database...");
        detectTechnology(context);
        analyzeJpaEntities(context);
        analyzeSqlFiles(context);
        inferJpaRelationships(context);
        deduplicateTables(context);
    }

    // ─── Technology detection ─────────────────────────────────────────────────

    private void detectTechnology(AnalysisContext context) {
        String tech = "Not detected", source = "Not detected", jdbcUrl = null,
               confidence = "UNKNOWN";
        boolean usesJpa = false;

        for (String content : new String[]{context.getAppPropertiesContent(), context.getAppYamlContent()}) {
            if (content == null) continue;
            if (jdbcUrl == null) {
                Matcher m = JDBC_URL.matcher(content);
                if (m.find()) {
                    jdbcUrl = m.group(1);
                    tech = fromJdbc(jdbcUrl);
                    source = "application config";
                    confidence = "DETECTED";
                }
            }
            if (content.contains("spring.jpa") || content.contains("jpa:") || content.contains("hibernate"))
                usesJpa = true;
        }

        if ("Not detected".equals(tech)) {
            for (FrameworkInfo fw : context.getDetectedFrameworks()) {
                if (List.of("MySQL","PostgreSQL","H2 (Embedded)","MariaDB","SQLite","Oracle").contains(fw.getName())) {
                    tech = fw.getName(); source = fw.getDetectionSource(); confidence = "DETECTED"; break;
                }
            }
        }

        if (!usesJpa)
            usesJpa = context.getDetectedFrameworks().stream()
                .anyMatch(f -> f.getName().contains("JPA") || f.getName().contains("Hibernate"));

        context.setDatabaseInfo(DatabaseInfo.builder()
            .technology(tech).detectionMethod(source).jdbcUrl(jdbcUrl)
            .confidence(confidence).usesJpa(usesJpa).usesHibernate(usesJpa).build());
    }

    private String fromJdbc(String url) {
        String l = url.toLowerCase();
        if (l.contains("mysql"))  return "MySQL";
        if (l.contains("postgres")) return "PostgreSQL";
        if (l.contains("h2"))     return "H2 (Embedded)";
        if (l.contains("oracle")) return "Oracle";
        if (l.contains("sqlserver") || l.contains("mssql")) return "SQL Server";
        if (l.contains("sqlite")) return "SQLite";
        if (l.contains("mariadb")) return "MariaDB";
        return "JDBC Database";
    }

    // ─── JPA entity analysis ──────────────────────────────────────────────────

    private void analyzeJpaEntities(AnalysisContext context) {
        for (AnalyzedFileDto file : context.getAnalyzedFiles()) {
            if (file.getClasses() == null) continue;
            for (ClassInfo cls : file.getClasses()) {
                if (!"ENTITY".equals(cls.getSpringRole())) continue;
                String src = context.getFileContents().get(file.getFilePath());
                String table = inferTableName(cls, src);
                if (context.getDatabaseTables().stream().noneMatch(t -> t.getTableName().equalsIgnoreCase(table))) {
                    context.getDatabaseTables().add(TableInfo.builder()
                        .tableName(table).entityClass(cls.getName())
                        .sourceFile(file.getFilePath()).detectionMethod("JPA_ENTITY")
                        .columns(extractEntityColumns(cls)).build());
                }
            }
        }
    }

    private String inferTableName(ClassInfo cls, String src) {
        if (src != null) {
            Matcher m = JPA_TABLE_NAME.matcher(src);
            if (m.find()) return m.group(1).toLowerCase();
        }
        return toSnake(cls.getName());
    }

    private List<ColumnInfo> extractEntityColumns(ClassInfo cls) {
        List<ColumnInfo> cols = new ArrayList<>();
        if (cls.getFields() == null) return cols;
        for (String field : cls.getFields()) {
            String[] p = field.trim().split("\\s+", 2);
            if (p.length < 2) continue;
            String type = p[0]; String name = p[1].replaceAll("[;,]","").trim();
            if (isCollection(type)) continue;
            boolean pk = name.equalsIgnoreCase("id");
            boolean fk = !pk && name.endsWith("Id");
            cols.add(ColumnInfo.builder()
                .columnName(toSnake(name)).dataType(javaToDb(type))
                .isPrimaryKey(pk).isForeignKey(fk).isNullable(!pk).isUnique(pk)
                .referencedTable(fk ? toSnake(name.substring(0, name.length()-2)) : null)
                .referencedColumn(fk ? "id" : null).build());
        }
        return cols;
    }

    private void inferJpaRelationships(AnalysisContext context) {
        Map<String, String> e2t = new HashMap<>();
        context.getDatabaseTables().forEach(t -> { if (t.getEntityClass() != null) e2t.put(t.getEntityClass(), t.getTableName()); });

        for (AnalyzedFileDto file : context.getAnalyzedFiles()) {
            String src = context.getFileContents().get(file.getFilePath());
            if (src == null || file.getClasses() == null) continue;
            for (ClassInfo cls : file.getClasses()) {
                if (!"ENTITY".equals(cls.getSpringRole())) continue;
                String ownerTable = e2t.get(cls.getName());
                if (ownerTable == null) continue;
                TableInfo ti = context.getDatabaseTables().stream().filter(t -> t.getTableName().equalsIgnoreCase(ownerTable)).findFirst().orElse(null);
                if (ti == null) continue;

                // @ManyToOne / @OneToOne with @JoinColumn(name="...")
                Pattern p = Pattern.compile("@(?:ManyToOne|OneToOne)[^@]*?@JoinColumn\\s*\\([^)]*name\\s*=\\s*\"([^\"]+)\"[^)]*\\)[^\\n@]*(?:private\\s+)?(\\w+)\\s+(\\w+)", Pattern.DOTALL);
                Matcher m = p.matcher(src);
                while (m.find()) {
                    String fkCol = m.group(1);
                    String relatedEntity = m.group(2);
                    String relTable = e2t.get(relatedEntity);
                    if (relTable == null) continue;
                    boolean exists = ti.getColumns() != null && ti.getColumns().stream().anyMatch(c -> c.getColumnName().equalsIgnoreCase(fkCol));
                    if (!exists) {
                        if (ti.getColumns() == null) ti.setColumns(new ArrayList<>());
                        ti.getColumns().add(ColumnInfo.builder()
                            .columnName(fkCol).dataType("BIGINT")
                            .isPrimaryKey(false).isForeignKey(true).isNullable(true).isUnique(false)
                            .referencedTable(relTable).referencedColumn("id").build());
                    }
                }
            }
        }
    }

    // ─── SQL file analysis ────────────────────────────────────────────────────

    private void analyzeSqlFiles(AnalysisContext context) {
        List<GitHubTreeEntry> files = new ArrayList<>(context.filesWithExtension("sql"));
        files.addAll(context.filesWithExtension("ddl"));
        if (files.isEmpty()) return;
        log.info("Analyzing {} SQL/DDL files", files.size());

        for (GitHubTreeEntry entry : files) {
            String p = entry.getPath();
            if (p.endsWith("data.sql") || p.contains("/data.sql") || p.contains("/data/")) continue;
            try {
                String content = gitHubService.fetchFileContent(context.getOwner(), context.getRepoName(), p, context.getBranch());
                if (content != null) parseSql(content, p, context);
            } catch (Exception e) {
                log.warn("SQL parse error in {}: {}", p, e.getMessage());
            }
        }
    }

    private void parseSql(String sql, String source, AnalysisContext context) {
        // Pre-scan for ALTER TABLE FK constraints
        Map<String, List<FkRef>> alterFks = new HashMap<>();
        Matcher altM = ALTER_FK.matcher(sql);
        while (altM.find()) {
            alterFks.computeIfAbsent(altM.group(1).toLowerCase(), k -> new ArrayList<>())
                .add(new FkRef(altM.group(2), altM.group(3), altM.group(4)));
        }

        Matcher createM = CREATE_TABLE_PATTERN.matcher(sql);
        while (createM.find()) {
            String tableName = createM.group(1).toLowerCase();
            String body = createM.group(2);
            log.debug("Parsing table: {}", tableName);

            // --- table-level PKs ---
            Set<String> pkCols = new LinkedHashSet<>();
            Matcher pkM = TABLE_PK.matcher(body);
            while (pkM.find()) {
                for (String c : pkM.group(1).split(","))
                    pkCols.add(c.trim().replaceAll("[`\"'\\[\\]]","").toLowerCase());
            }

            // --- table-level FKs ---
            Map<String, FkRef> tableFks = new LinkedHashMap<>();
            Matcher fkM = TABLE_FK.matcher(body);
            while (fkM.find())
                tableFks.put(fkM.group(1).toLowerCase(), new FkRef(fkM.group(1), fkM.group(2), fkM.group(3)));

            // --- ALTER TABLE FKs ---
            if (alterFks.containsKey(tableName))
                alterFks.get(tableName).forEach(fk -> tableFks.put(fk.col.toLowerCase(), fk));

            // --- Parse column lines ---
            List<ColumnInfo> columns = new ArrayList<>();
            for (String rawLine : body.split(",")) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                String upper = line.toUpperCase();

                // Skip constraint-only lines
                if (upper.startsWith("PRIMARY KEY") || upper.startsWith("FOREIGN KEY")
                    || upper.startsWith("UNIQUE KEY") || upper.startsWith("INDEX")
                    || upper.startsWith("KEY ") || upper.startsWith("CONSTRAINT")
                    || upper.startsWith("UNIQUE (") || upper.startsWith("ENGINE")
                    || upper.startsWith(")")) continue;

                // First token = column name
                String[] tokens = line.split("\\s+");
                if (tokens.length < 2) continue;
                String colName = tokens[0].replaceAll("[`\"'\\[\\]]", "");
                if (colName.isEmpty() || colName.startsWith("--")) continue;

                // Second token = data type (strip parentheses like INT(4) -> INT)
                String rawType = tokens[1].replaceAll("\\([^)]*\\)", "").toUpperCase();
                // Clean trailing punctuation from type
                rawType = rawType.replaceAll("[^A-Z]", "");
                if (rawType.isEmpty()) rawType = "VARCHAR";

                // *** Inline PRIMARY KEY detection ***
                // The whole line (not just third token) is checked
                boolean isPk = upper.contains("PRIMARY KEY") || pkCols.contains(colName.toLowerCase());

                // *** FK detection ***
                boolean isFk = false;
                String refTable = null, refCol = null;

                if (tableFks.containsKey(colName.toLowerCase())) {
                    FkRef fk = tableFks.get(colName.toLowerCase());
                    isFk = true; refTable = fk.refTable.toLowerCase(); refCol = fk.refCol.toLowerCase();
                } else {
                    // Inline REFERENCES
                    Matcher rm = INLINE_REF.matcher(line);
                    if (rm.find()) {
                        isFk = true; refTable = rm.group(1).toLowerCase(); refCol = rm.group(2).toLowerCase();
                    }
                }

                boolean notNull = upper.contains("NOT NULL") || isPk;

                columns.add(ColumnInfo.builder()
                    .columnName(colName).dataType(rawType)
                    .isPrimaryKey(isPk).isForeignKey(isFk)
                    .isNullable(!notNull).isUnique(isPk)
                    .referencedTable(refTable).referencedColumn(refCol).build());

                log.debug("  {} {} pk={} fk={} ref={}", colName, rawType, isPk, isFk, refTable);
            }

            if (columns.isEmpty()) continue;

            long pkCount = columns.stream().filter(ColumnInfo::isPrimaryKey).count();
            long fkCount = columns.stream().filter(ColumnInfo::isForeignKey).count();
            log.info("Table {}: {} cols, {} PKs, {} FKs (source: {})", tableName, columns.size(), pkCount, fkCount, source);

            // SQL definition takes priority over JPA inference
            context.getDatabaseTables().removeIf(t -> t.getTableName().equalsIgnoreCase(tableName));
            context.getDatabaseTables().add(TableInfo.builder()
                .tableName(tableName).sourceFile(source)
                .detectionMethod("SQL_CREATE").columns(columns).build());
        }
    }

    private void deduplicateTables(AnalysisContext context) {
        Map<String, TableInfo> best = new LinkedHashMap<>();
        for (TableInfo t : context.getDatabaseTables()) {
            String key = t.getTableName().toLowerCase();
            TableInfo ex = best.get(key);
            if (ex == null || isBetter(t, ex)) best.put(key, t);
        }
        context.getDatabaseTables().clear();
        context.getDatabaseTables().addAll(best.values());
    }

    private boolean isBetter(TableInfo candidate, TableInfo existing) {
        if ("SQL_CREATE".equals(candidate.getDetectionMethod()) && !"SQL_CREATE".equals(existing.getDetectionMethod())) return true;
        int cc = candidate.getColumns() == null ? 0 : candidate.getColumns().size();
        int ec = existing.getColumns() == null ? 0 : existing.getColumns().size();
        return cc > ec;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private record FkRef(String col, String refTable, String refCol) {}

    private static String toSnake(String s) { return s.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(); }
    private static boolean isCollection(String t) { return t.startsWith("List")||t.startsWith("Set")||t.startsWith("Collection")||t.startsWith("Map")||t.contains("[]"); }
    private static String javaToDb(String t) {
        return switch(t.toLowerCase()) {
            case "string" -> "VARCHAR"; case "long","integer","int","short" -> "BIGINT";
            case "double","float" -> "DECIMAL"; case "boolean" -> "BOOLEAN";
            case "localdatetime","date","timestamp" -> "TIMESTAMP"; case "localdate" -> "DATE";
            case "bigdecimal" -> "DECIMAL"; case "byte[]" -> "BLOB"; default -> "VARCHAR";
        };
    }
}
