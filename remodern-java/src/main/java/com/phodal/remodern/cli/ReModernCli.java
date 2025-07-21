package com.phodal.remodern.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phodal.remodern.core.McpTool;
import com.phodal.remodern.core.McpToolRegistry;
import com.phodal.remodern.core.McpToolResult;
import com.phodal.remodern.tools.bytecode.ByteCodeTool;
import com.phodal.remodern.tools.codegen.AstCodeGenTool;
import com.phodal.remodern.tools.codegen.TemplateCodeGenTool;
import com.phodal.remodern.tools.migration.OpenRewriteTool;
import com.phodal.remodern.tools.parsing.JSPParseTool;
import com.phodal.remodern.tools.parsing.JavaParseTool;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Main CLI application for ReModern Java tools
 */
@Command(
    name = "remodern",
    description = "ReModern Java - MCP Tools for Java code generation, parsing, and migration",
    version = "1.0.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        ReModernCli.ListCommand.class,
        ReModernCli.RunCommand.class,
        ReModernCli.InfoCommand.class
    }
)
public class ReModernCli implements Callable<Integer> {
    
    private static final McpToolRegistry toolRegistry = new McpToolRegistry();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        // Register all available tools
        toolRegistry.registerTool(new AstCodeGenTool());
        toolRegistry.registerTool(new TemplateCodeGenTool());
        toolRegistry.registerTool(new OpenRewriteTool());
        toolRegistry.registerTool(new JSPParseTool());
        toolRegistry.registerTool(new ByteCodeTool());
        toolRegistry.registerTool(new JavaParseTool());
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ReModernCli()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() {
        System.out.println("ReModern Java - MCP Tools for Java development");
        System.out.println("Use 'remodern --help' for more information.");
        System.out.println("Use 'remodern list' to see available tools.");
        return 0;
    }
    
    @Command(name = "list", description = "List all available MCP tools")
    static class ListCommand implements Callable<Integer> {
        
        @Option(names = {"-c", "--category"}, description = "Filter by category")
        private String category;
        
        @Option(names = {"-v", "--verbose"}, description = "Show detailed information")
        private boolean verbose;
        
        @Override
        public Integer call() {
            System.out.println("Available MCP Tools:");
            System.out.println("===================");
            
            var tools = category != null ? 
                toolRegistry.getToolsByCategory(category) : 
                toolRegistry.getAllTools();
            
            if (tools.isEmpty()) {
                System.out.println("No tools found" + (category != null ? " in category: " + category : ""));
                return 0;
            }
            
            for (McpTool tool : tools) {
                System.out.printf("• %s (%s)%n", tool.getName(), tool.getCategory());
                if (verbose) {
                    System.out.printf("  Description: %s%n", tool.getDescription());
                    System.out.printf("  Schema: %s%n", tool.getInputSchema().toString());
                    System.out.println();
                }
            }
            
            if (!verbose) {
                System.out.println("\nUse --verbose for detailed information");
                System.out.println("Use 'remodern info <tool-name>' for tool-specific help");
            }
            
            return 0;
        }
    }
    
    @Command(name = "run", description = "Run a specific MCP tool")
    static class RunCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Tool name to run")
        private String toolName;
        
        @Option(names = {"-p", "--param"}, description = "Tool parameters in key=value format")
        private Map<String, String> parameters = new HashMap<>();
        
        @Option(names = {"-f", "--file"}, description = "JSON file containing parameters")
        private String parameterFile;
        
        @Option(names = {"-o", "--output"}, description = "Output format (json, yaml, text)")
        private String outputFormat = "json";
        
        @Option(names = {"--dry-run"}, description = "Show what would be executed without running")
        private boolean dryRun;
        
        @Override
        public Integer call() {
            try {
                McpTool tool = toolRegistry.getTool(toolName);
                if (tool == null) {
                    System.err.println("Tool not found: " + toolName);
                    System.err.println("Use 'remodern list' to see available tools");
                    return 1;
                }
                
                Map<String, Object> params = prepareParameters();
                
                if (dryRun) {
                    System.out.println("Would execute tool: " + toolName);
                    System.out.println("With parameters: " + objectMapper.writeValueAsString(params));
                    return 0;
                }
                
                System.out.println("Executing tool: " + toolName);
                McpToolResult result = tool.execute(params);
                
                outputResult(result);
                
                return result.isSuccess() ? 0 : 1;
                
            } catch (Exception e) {
                System.err.println("Error executing tool: " + e.getMessage());
                if (System.getProperty("debug") != null) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
        
        private Map<String, Object> prepareParameters() throws Exception {
            Map<String, Object> params = new HashMap<>();
            
            // Load from file if specified
            if (parameterFile != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fileParams = objectMapper.readValue(
                    new java.io.File(parameterFile), Map.class);
                params.putAll(fileParams);
            }
            
            // Override with command line parameters
            params.putAll(parameters);
            
            return params;
        }
        
        private void outputResult(McpToolResult result) throws Exception {
            switch (outputFormat.toLowerCase()) {
                case "json":
                    System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(result));
                    break;
                case "yaml":
                    // For simplicity, using JSON format for now
                    // In a real implementation, you might want to add YAML support
                    System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(result));
                    break;
                case "text":
                    outputTextResult(result);
                    break;
                default:
                    System.err.println("Unsupported output format: " + outputFormat);
            }
        }
        
        private void outputTextResult(McpToolResult result) {
            System.out.println("=== Tool Execution Result ===");
            System.out.println("Success: " + result.isSuccess());
            System.out.println("Timestamp: " + result.getTimestamp());
            
            if (result.isSuccess()) {
                System.out.println("Content: " + result.getContent());
                if (!result.getMetadata().isEmpty()) {
                    System.out.println("Metadata:");
                    result.getMetadata().forEach((key, value) -> 
                        System.out.println("  " + key + ": " + value));
                }
            } else {
                System.out.println("Error: " + result.getError());
            }
        }
    }
    
    @Command(name = "info", description = "Show detailed information about a specific tool")
    static class InfoCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Tool name")
        private String toolName;
        
        @Override
        public Integer call() {
            try {
                McpTool tool = toolRegistry.getTool(toolName);
                if (tool == null) {
                    System.err.println("Tool not found: " + toolName);
                    System.err.println("Use 'remodern list' to see available tools");
                    return 1;
                }
                
                System.out.println("Tool Information");
                System.out.println("================");
                System.out.println("Name: " + tool.getName());
                System.out.println("Category: " + tool.getCategory());
                System.out.println("Description: " + tool.getDescription());
                System.out.println();
                
                System.out.println("Input Schema:");
                System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(tool.getInputSchema()));
                
                System.out.println("\nSupported Operations:");
                String[] commonOps = {"generate", "parse", "analyze", "migrate", "refactor"};
                for (String op : commonOps) {
                    if (tool.supportsOperation(op)) {
                        System.out.println("• " + op);
                    }
                }
                
                System.out.println("\nExample Usage:");
                System.out.println("remodern run " + toolName + " -p param1=value1 -p param2=value2");
                
                return 0;
                
            } catch (Exception e) {
                System.err.println("Error getting tool information: " + e.getMessage());
                return 1;
            }
        }
    }
}
