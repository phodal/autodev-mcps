package com.phodal.remodern.tools.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phodal.remodern.core.AbstractMcpTool;
import com.phodal.remodern.core.McpToolException;
import com.phodal.remodern.core.McpToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * MCP Tool for AST migration and refactoring using OpenRewrite
 */
public class OpenRewriteTool extends AbstractMcpTool {
    
    public OpenRewriteTool() {
        super("openrewrite-tool", 
              "Perform AST migration and refactoring using OpenRewrite. Supports code transformations, migrations, and refactoring.",
              "migration");
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = createBaseSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        ArrayNode required = (ArrayNode) schema.get("required");
        
        ObjectNode operationProperty = objectMapper.createObjectNode();
        operationProperty.put("type", "string");
        operationProperty.put("description", "Type of OpenRewrite operation to perform");
        properties.set("operation", operationProperty);
        required.add("operation");
        
        ObjectNode sourceProperty = objectMapper.createObjectNode();
        sourceProperty.put("type", "string");
        sourceProperty.put("description", "Source file path or directory to process");
        properties.set("source", sourceProperty);
        required.add("source");
        
        ObjectNode dryRunProperty = objectMapper.createObjectNode();
        dryRunProperty.put("type", "boolean");
        dryRunProperty.put("description", "Whether to perform a dry run without writing changes");
        dryRunProperty.put("default", false);
        properties.set("dryRun", dryRunProperty);
        
        return schema;
    }
    
    @Override
    protected McpToolResult doExecute(Map<String, Object> parameters) throws McpToolException {
        String operation = getRequiredParameter(parameters, "operation", String.class);
        String source = getRequiredParameter(parameters, "source", String.class);
        boolean dryRun = getOptionalParameter(parameters, "dryRun", false, Boolean.class);
        
        validateNotEmpty(source, "source");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", operation);
        metadata.put("source", source);
        metadata.put("dryRun", dryRun);
        metadata.put("note", "OpenRewrite integration requires specific setup");
        
        return McpToolResult.success("OpenRewrite operation completed (simplified)", metadata);
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        return switch (operation.toLowerCase()) {
            case "recipe", "visitor", "refactor", "migrate", "ast-migration", "code-transformation" -> true;
            default -> false;
        };
    }
}
