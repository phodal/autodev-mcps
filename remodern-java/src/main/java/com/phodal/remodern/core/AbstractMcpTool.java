package com.phodal.remodern.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Abstract base class for MCP tools providing common functionality
 */
public abstract class AbstractMcpTool implements McpTool {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String name;
    private final String description;
    private final String category;
    
    protected AbstractMcpTool(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public String getCategory() {
        return category;
    }
    
    @Override
    public McpToolResult execute(Map<String, Object> parameters) throws McpToolException {
        try {
            logger.info("Executing tool '{}' with parameters: {}", name, parameters);
            
            // Validate parameters
            validateParameters(parameters);
            
            // Execute the tool-specific logic
            McpToolResult result = doExecute(parameters);
            
            logger.info("Tool '{}' executed successfully", name);
            return result;
            
        } catch (McpToolException e) {
            logger.error("Tool '{}' execution failed: {}", name, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in tool '{}': {}", name, e.getMessage(), e);
            throw new McpToolException(name, "EXECUTION_ERROR", "Unexpected error during execution", e);
        }
    }
    
    /**
     * Template method for tool-specific execution logic
     * @param parameters validated input parameters
     * @return execution result
     * @throws McpToolException if execution fails
     */
    protected abstract McpToolResult doExecute(Map<String, Object> parameters) throws McpToolException;
    
    /**
     * Helper method to create a basic JSON schema
     * @return basic object schema
     */
    protected ObjectNode createBaseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        schema.set("required", objectMapper.createArrayNode());
        return schema;
    }
    
    /**
     * Helper method to get a required parameter
     * @param parameters parameter map
     * @param key parameter key
     * @param type expected type
     * @param <T> parameter type
     * @return parameter value
     * @throws McpToolException if parameter is missing or wrong type
     */
    @SuppressWarnings("unchecked")
    protected <T> T getRequiredParameter(Map<String, Object> parameters, String key, Class<T> type) 
            throws McpToolException {
        Object value = parameters.get(key);
        if (value == null) {
            throw new McpToolException(name, "MISSING_PARAMETER", 
                    "Required parameter '" + key + "' is missing");
        }
        
        if (!type.isInstance(value)) {
            throw new McpToolException(name, "INVALID_PARAMETER_TYPE", 
                    "Parameter '" + key + "' must be of type " + type.getSimpleName());
        }
        
        return (T) value;
    }
    
    /**
     * Helper method to get an optional parameter with default value
     * @param parameters parameter map
     * @param key parameter key
     * @param defaultValue default value if parameter is missing
     * @param type expected type
     * @param <T> parameter type
     * @return parameter value or default
     * @throws McpToolException if parameter is wrong type
     */
    @SuppressWarnings("unchecked")
    protected <T> T getOptionalParameter(Map<String, Object> parameters, String key, T defaultValue, Class<T> type) 
            throws McpToolException {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        if (!type.isInstance(value)) {
            throw new McpToolException(name, "INVALID_PARAMETER_TYPE", 
                    "Parameter '" + key + "' must be of type " + type.getSimpleName());
        }
        
        return (T) value;
    }
    
    /**
     * Helper method to validate that a string parameter is not empty
     * @param value string value to validate
     * @param parameterName parameter name for error messages
     * @throws McpToolException if string is null or empty
     */
    protected void validateNotEmpty(String value, String parameterName) throws McpToolException {
        if (value == null || value.trim().isEmpty()) {
            throw new McpToolException(name, "EMPTY_PARAMETER", 
                    "Parameter '" + parameterName + "' cannot be empty");
        }
    }
}
