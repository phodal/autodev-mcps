package com.phodal.remodern.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Base interface for all MCP tools in ReModern Java
 * 
 * This interface defines the contract that all tools must implement
 * to be compatible with the Model Context Protocol (MCP) framework.
 */
public interface McpTool {
    
    /**
     * Get the unique name of this tool
     * @return tool name
     */
    String getName();
    
    /**
     * Get the description of what this tool does
     * @return tool description
     */
    String getDescription();
    
    /**
     * Get the input schema for this tool's parameters
     * @return JSON schema describing the expected input parameters
     */
    JsonNode getInputSchema();
    
    /**
     * Execute the tool with the given parameters
     * @param parameters input parameters as a map
     * @return execution result
     * @throws McpToolException if execution fails
     */
    McpToolResult execute(Map<String, Object> parameters) throws McpToolException;
    
    /**
     * Validate the input parameters before execution
     * @param parameters input parameters to validate
     * @throws McpToolException if validation fails
     */
    default void validateParameters(Map<String, Object> parameters) throws McpToolException {
        // Default implementation - can be overridden by specific tools
    }
    
    /**
     * Get the category of this tool (e.g., "code-generation", "parsing", "migration")
     * @return tool category
     */
    default String getCategory() {
        return "general";
    }
    
    /**
     * Check if this tool supports the given operation
     * @param operation operation name
     * @return true if supported, false otherwise
     */
    default boolean supportsOperation(String operation) {
        return false;
    }
}
