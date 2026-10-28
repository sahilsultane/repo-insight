package com.githubrepoinsight.analyzer;

import com.githubrepoinsight.dto.AnalysisResultDto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds architecture graph (nodes + edges) for React Flow visualization.
 * Only includes layers that are actually present in the codebase.
 */
@Component
@Slf4j
public class ArchitectureGraphBuilder {

    public GraphData buildArchitectureGraph(AnalysisContext context) {
        log.info("Building architecture graph...");
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        // Detect which architectural layers are present
        Set<String> presentRoles = new HashSet<>();
        Map<String, List<String>> roleToClasses = new LinkedHashMap<>();

        for (AnalyzedFileDto file : context.getAnalyzedFiles()) {
            if (file.getClasses() == null) continue;
            for (ClassInfo cls : file.getClasses()) {
                if (cls.getSpringRole() != null) {
                    presentRoles.add(cls.getSpringRole());
                    roleToClasses.computeIfAbsent(cls.getSpringRole(), k -> new ArrayList<>())
                        .add(cls.getName());
                }
            }
        }

        // Determine database node
        boolean hasDatabase = context.getDatabaseInfo() != null
            && !"Not detected".equals(context.getDatabaseInfo().getTechnology());
        String dbTech = hasDatabase ? context.getDatabaseInfo().getTechnology() : null;

        // Build layer nodes in logical order
        int x = 100, y = 60;
        int yStep = 160;
        List<String> layerOrder = List.of("CONTROLLER", "SERVICE", "REPOSITORY", "ENTITY");

        String prevNodeId = null;

        // Add external client node at top
        nodes.add(createNode("client", "HTTP Client / Frontend", "CLIENT", x, y,
            Map.of("description", "External API consumer or web browser")));
        prevNodeId = "client";
        y += yStep;

        for (String role : layerOrder) {
            if (!presentRoles.contains(role)) continue;

            String nodeId = role.toLowerCase();
            String label = roleLabel(role);
            List<String> classes = roleToClasses.getOrDefault(role, List.of());
            String classesStr = String.join(", ", classes.stream().limit(3).toList());
            if (classes.size() > 3) classesStr += " ...+" + (classes.size() - 3) + " more";

            nodes.add(createNode(nodeId, label, role, x, y,
                Map.of("classes", classesStr, "count", classes.size())));

            if (prevNodeId != null) {
                edges.add(GraphEdge.builder()
                    .id(prevNodeId + "-" + nodeId)
                    .source(prevNodeId)
                    .target(nodeId)
                    .label(edgeLabel(prevNodeId, nodeId))
                    .type("smoothstep")
                    .build());
            }

            prevNodeId = nodeId;
            y += yStep;
        }

        // Add CONFIG nodes (off to the side)
        if (presentRoles.contains("CONFIG")) {
            List<String> configs = roleToClasses.getOrDefault("CONFIG", List.of());
            nodes.add(createNode("config", "Configuration", "CONFIG", x + 300, 200,
                Map.of("classes", String.join(", ", configs.stream().limit(3).toList()))));
        }

        // Add DATABASE node
        if (hasDatabase) {
            nodes.add(createNode("database", dbTech, "DATABASE", x, y,
                Map.of("type", dbTech, "tables", context.getDatabaseTables().size())));

            if (prevNodeId != null) {
                edges.add(GraphEdge.builder()
                    .id(prevNodeId + "-database")
                    .source(prevNodeId)
                    .target("database")
                    .label("SQL / JDBC")
                    .type("smoothstep")
                    .build());
            }
        }

        // Add COMPONENT nodes (if any, off to the side)
        if (presentRoles.contains("COMPONENT")) {
            List<String> components = roleToClasses.getOrDefault("COMPONENT", List.of());
            nodes.add(createNode("components", "Components", "COMPONENT", x + 300, 400,
                Map.of("classes", String.join(", ", components.stream().limit(3).toList()))));
        }

        log.info("Architecture graph: {} nodes, {} edges", nodes.size(), edges.size());
        return GraphData.builder().nodes(nodes).edges(edges).build();
    }

    public GraphData buildErDiagram(AnalysisContext context) {
        log.info("Building ER diagram...");
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        List<TableInfo> tables = context.getDatabaseTables();
        if (tables.isEmpty()) {
            return GraphData.builder().nodes(nodes).edges(edges).build();
        }

        int cols = Math.min(3, tables.size());
        int x = 60, y = 60;
        int xStep = 380, yStep = 300;

        for (int i = 0; i < tables.size(); i++) {
            TableInfo table = tables.get(i);
            int col = i % cols;
            int row = i / cols;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tableName", table.getTableName());
            data.put("entityClass", table.getEntityClass());
            data.put("detectionMethod", table.getDetectionMethod());

            List<Map<String, Object>> colData = new ArrayList<>();
            if (table.getColumns() != null) {
                for (ColumnInfo col2 : table.getColumns()) {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("name", col2.getColumnName());
                    cm.put("type", col2.getDataType());
                    cm.put("pk", col2.isPrimaryKey());
                    cm.put("fk", col2.isForeignKey());
                    cm.put("nullable", col2.isNullable());
                    cm.put("unique", col2.isUnique());
                    if (col2.getReferencedTable() != null) {
                        cm.put("ref", col2.getReferencedTable() + "." + col2.getReferencedColumn());
                    }
                    colData.add(cm);
                }
            }
            data.put("columns", colData);

            nodes.add(createNode(
                "table_" + table.getTableName(),
                table.getTableName(),
                "DB_TABLE",
                x + col * xStep,
                y + row * yStep,
                data
            ));
        }

        // Create FK edges
        Set<String> addedEdges = new HashSet<>();
        for (TableInfo table : tables) {
            if (table.getColumns() == null) continue;
            for (ColumnInfo col : table.getColumns()) {
                if (!col.isForeignKey() || col.getReferencedTable() == null) continue;

                boolean targetExists = tables.stream()
                    .anyMatch(t -> t.getTableName().equalsIgnoreCase(col.getReferencedTable()));
                if (!targetExists) continue;

                String edgeId = "table_" + table.getTableName() + "-table_" + col.getReferencedTable();
                if (addedEdges.contains(edgeId)) continue;
                addedEdges.add(edgeId);

                edges.add(GraphEdge.builder()
                    .id(edgeId)
                    .source("table_" + table.getTableName())
                    .target("table_" + col.getReferencedTable())
                    .label("FK: " + col.getColumnName())
                    .type("smoothstep")
                    .build());
            }
        }

        log.info("ER diagram: {} table nodes, {} FK edges", nodes.size(), edges.size());
        return GraphData.builder().nodes(nodes).edges(edges).build();
    }

    // =================== Helpers ===================

    private GraphNode createNode(String id, String label, String type, int x, int y, Map<String, Object> extra) {
        Map<String, Object> position = Map.of("x", x, "y", y);
        Map<String, Object> data = new LinkedHashMap<>(extra);
        data.put("label", label);
        data.put("type", type);
        return GraphNode.builder()
            .id(id)
            .label(label)
            .type(type)
            .data(data)
            .position(position)
            .build();
    }

    private String roleLabel(String role) {
        return switch (role) {
            case "CONTROLLER" -> "Controller Layer";
            case "SERVICE" -> "Service Layer";
            case "REPOSITORY" -> "Repository Layer";
            case "ENTITY" -> "Entity Layer";
            case "CONFIG" -> "Configuration";
            case "COMPONENT" -> "Components";
            default -> role;
        };
    }

    private String edgeLabel(String from, String to) {
        if ("client".equals(from)) return "HTTP Request";
        if ("controller".equals(from) && "service".equals(to)) return "calls";
        if ("service".equals(from) && "repository".equals(to)) return "uses";
        if ("repository".equals(from) && "entity".equals(to)) return "maps";
        if ("entity".equals(from) || "repository".equals(from)) return "queries";
        return "";
    }
}
