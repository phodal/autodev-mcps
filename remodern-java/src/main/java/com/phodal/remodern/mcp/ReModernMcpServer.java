package com.phodal.remodern.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phodal.remodern.core.McpTool;
import com.phodal.remodern.core.McpToolRegistry;
import com.phodal.remodern.core.McpToolResult;
import com.phodal.remodern.tools.bytecode.ByteCodeTool;
import com.phodal.remodern.tools.codegen.AstCodeGenTool;
import com.phodal.remodern.tools.codegen.TemplateCodeGenTool;
import com.phodal.remodern.tools.migration.OpenRewriteTool;
import com.phodal.remodern.tools.parsing.JSPParseTool;
import com.phodal.remodern.tools.parsing.JavaParseTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP Server implementation for ReModern Java tools using STDIO transport
 *
 * This implements the standard MCP protocol over STDIO using JSON-RPC 2.0
 */
public class ReModernMcpServer {

    private static final Logger logger = LoggerFactory.getLogger(ReModernMcpServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final McpToolRegistry toolRegistry;
    private final BufferedReader stdin;
    private final PrintWriter stdout;
    private boolean running = false;

    public ReModernMcpServer() {
        this.toolRegistry = new McpToolRegistry();
        this.stdin = new BufferedReader(new InputStreamReader(System.in));
        this.stdout = new PrintWriter(System.out, true);
        initializeTools();
    }

    private void initializeTools() {
        // Register all available tools
        toolRegistry.registerTool(new AstCodeGenTool());
        toolRegistry.registerTool(new TemplateCodeGenTool());
        toolRegistry.registerTool(new OpenRewriteTool());
        toolRegistry.registerTool(new JSPParseTool());
        toolRegistry.registerTool(new ByteCodeTool());
        toolRegistry.registerTool(new JavaParseTool());

        logger.info("Registered {} MCP tools", toolRegistry.getToolCount());
    }

    public void start() throws IOException {
        logger.info("Starting ReModern MCP Server with STDIO transport");
        running = true;

        String line;
        while (running && (line = stdin.readLine()) != null) {
            try {
                JsonNode request = objectMapper.readTree(line);
                JsonNode response = handleRequest(request);
                if (response != null) {
                    stdout.println(objectMapper.writeValueAsString(response));
                }
            } catch (Exception e) {
                logger.error("Error processing request: " + line, e);
                JsonNode errorResponse = createErrorResponse(null, -32603, "Internal error", e.getMessage());
                stdout.println(objectMapper.writeValueAsString(errorResponse));
            }
        }
    }

    private JsonNode handleRequest(JsonNode request) throws Exception {
        String method = request.path("method").asText();
        JsonNode id = request.path("id");
        JsonNode params = request.path("params");

        logger.debug("Handling request: method={}, id={}", method, id);

        return switch (method) {
            case "initialize" -> handleInitialize(id, params);
            case "tools/list" -> handleToolsList(id);
            case "tools/call" -> handleToolCall(id, params);
            case "notifications/initialized" -> null; // No response needed for notifications
            default -> createErrorResponse(id, -32601, "Method not found", "Unknown method: " + method);
        };
    }

    private JsonNode handleInitialize(JsonNode id, JsonNode params) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "remodern-java");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.put("tools", true);
        result.set("capabilities", capabilities);

        response.set("result", result);

        logger.info("Initialized MCP server");
        return response;
    }

    private JsonNode handleToolsList(JsonNode id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = objectMapper.createArrayNode();

        for (McpTool tool : toolRegistry.getAllTools()) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("name", tool.getName());
            toolNode.put("description", tool.getDescription());
            toolNode.set("inputSchema", tool.getInputSchema());
            tools.add(toolNode);
        }

        result.set("tools", tools);
        response.set("result", result);

        logger.debug("Listed {} tools", tools.size());
        return response;
    }

    private JsonNode handleToolCall(JsonNode id, JsonNode params) {
        try {
            String toolName = params.path("name").asText();
            JsonNode arguments = params.path("arguments");

            McpTool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                return createErrorResponse(id, -32602, "Invalid params", "Tool not found: " + toolName);
            }

            // Convert arguments to Map
            @SuppressWarnings("unchecked")
            Map<String, Object> argumentsMap = objectMapper.convertValue(arguments, Map.class);

            // Execute tool
            McpToolResult toolResult = tool.execute(argumentsMap);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);

            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode content = objectMapper.createArrayNode();

            ObjectNode contentItem = objectMapper.createObjectNode();
            contentItem.put("type", "text");

            if (toolResult.isSuccess()) {
                // Format successful result
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("success", true);
                resultData.put("content", toolResult.getContent());
                resultData.put("metadata", toolResult.getMetadata());
                resultData.put("timestamp", toolResult.getTimestamp().toString());

                contentItem.put("text", objectMapper.writeValueAsString(resultData));
            } else {
                // Format error result
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("success", false);
                errorData.put("error", toolResult.getError());
                errorData.put("timestamp", toolResult.getTimestamp().toString());

                contentItem.put("text", objectMapper.writeValueAsString(errorData));
            }

            content.add(contentItem);
            result.set("content", content);
            response.set("result", result);

            logger.debug("Executed tool: {} with result: {}", toolName, toolResult.isSuccess());
            return response;

        } catch (Exception e) {
            logger.error("Error executing tool", e);
            return createErrorResponse(id, -32603, "Internal error", e.getMessage());
        }
    }

    private JsonNode createErrorResponse(JsonNode id, int code, String message, String data) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }

        response.set("error", error);
        return response;
    }

    public void stop() {
        running = false;
        logger.info("MCP server stopped");
    }

    public static void main(String[] args) {
        // Disable logging to stdout to avoid interfering with JSON-RPC
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");

        ReModernMcpServer server = new ReModernMcpServer();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down MCP server...");
            server.stop();
        }));

        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
