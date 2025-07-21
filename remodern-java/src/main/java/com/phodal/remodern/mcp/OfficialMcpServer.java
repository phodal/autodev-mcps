package com.phodal.remodern.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phodal.remodern.core.McpTool;
import com.phodal.remodern.core.McpToolResult;
import com.phodal.remodern.tools.bytecode.ByteCodeTool;
import com.phodal.remodern.tools.codegen.AstCodeGenTool;
import com.phodal.remodern.tools.codegen.TemplateCodeGenTool;
import com.phodal.remodern.tools.migration.OpenRewriteTool;
import com.phodal.remodern.tools.parsing.JSPParseTool;
import com.phodal.remodern.tools.parsing.JavaParseTool;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP Server implementation for ReModern Java tools using official MCP SDK
 *
 * This implements the standard MCP protocol using the official Java SDK
 */
public class OfficialMcpServer {
    private static final Logger logger = LoggerFactory.getLogger(OfficialMcpServer.class);
    private final List<McpTool> tools;
    private McpSyncServer mcpServer;

    public OfficialMcpServer() {
        this.tools = new ArrayList<>();
        initializeTools();
    }

    private void initializeTools() {
        // Register all available tools
        tools.add(new AstCodeGenTool());
        tools.add(new TemplateCodeGenTool());
        tools.add(new OpenRewriteTool());
        tools.add(new JSPParseTool());
        tools.add(new ByteCodeTool());
        tools.add(new JavaParseTool());

        logger.info("Registered {} MCP tools", tools.size());
    }

    /**
     * Convert our custom McpTool to official SDK Tool Specification
     */
    private McpServerFeatures.SyncToolSpecification createToolSpecification(McpTool tool) {
        try {
            // Create the Tool definition with proper JSON schema
            String schemaString = tool.getInputSchema().toString();
            logger.debug("Tool {} schema: {}", tool.getName(), schemaString);

            Tool toolDef = new Tool(
                tool.getName(),
                tool.getDescription(),
                schemaString
            );

            // Create the tool handler
            return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    try {
                        logger.debug("Executing tool {} with arguments: {}", tool.getName(), arguments);

                        // Execute the tool
                        McpToolResult result = tool.execute(arguments);

                        // Convert result to CallToolResult
                        String contentText;
                        boolean isError = !result.isSuccess();

                        if (result.isSuccess()) {
                            contentText = result.getContent() != null ? result.getContent().toString() : "Success";
                        } else {
                            contentText = result.getError() != null ? result.getError() : "Unknown error";
                        }

                        logger.debug("Tool {} execution result: success={}, content={}",
                                   tool.getName(), result.isSuccess(), contentText);

                        return new CallToolResult(
                            List.of(new McpSchema.TextContent(contentText)),
                            isError
                        );

                    } catch (Exception e) {
                        logger.error("Error executing tool {}: {}", tool.getName(), e.getMessage(), e);
                        return new CallToolResult(
                            List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
                            true
                        );
                    }
                }
            );
        } catch (Exception e) {
            logger.error("Error creating tool specification for {}: {}", tool.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to create tool specification for " + tool.getName(), e);
        }
    }

    public void start() throws Exception {
        logger.info("Starting ReModern MCP Server with official SDK");

        // Create transport provider
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new ObjectMapper());

        // Create server capabilities
        ServerCapabilities capabilities = ServerCapabilities.builder()
            .tools(true)  // Enable tool support
            .logging()    // Enable logging support
            .build();

        // Create tool specifications
        McpServerFeatures.SyncToolSpecification[] toolSpecs = new McpServerFeatures.SyncToolSpecification[tools.size()];
        for (int i = 0; i < tools.size(); i++) {
            McpTool tool = tools.get(i);
            toolSpecs[i] = createToolSpecification(tool);
            logger.debug("Created tool specification: {}", tool.getName());
        }

        // Create the MCP server with all tools
        mcpServer = McpServer.sync(transportProvider)
            .serverInfo("remodern-java", "1.0.0")
            .capabilities(capabilities)
            .tools(toolSpecs)
            .build();

        logger.info("MCP Server started successfully with {} tools", tools.size());

        // Keep the server running - the SDK will handle the message loop
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        // Block to keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Server interrupted, shutting down...");
        }
    }

    public void stop() {
        if (mcpServer != null) {
            try {
                mcpServer.close();
                logger.info("MCP server stopped");
            } catch (Exception e) {
                logger.error("Error stopping MCP server", e);
            }
        }
    }

    public static void main(String[] args) {
        // Configure logging to stderr to avoid interfering with JSON-RPC
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");

        OfficialMcpServer server = new OfficialMcpServer();

        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
