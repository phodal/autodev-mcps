package com.phodal.remodern.core;

/**
 * Exception thrown by MCP tools during execution
 */
public class McpToolException extends Exception {
    
    private final String toolName;
    private final String errorCode;
    
    public McpToolException(String message) {
        super(message);
        this.toolName = null;
        this.errorCode = null;
    }
    
    public McpToolException(String message, Throwable cause) {
        super(message, cause);
        this.toolName = null;
        this.errorCode = null;
    }
    
    public McpToolException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
        this.errorCode = null;
    }
    
    public McpToolException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
        this.errorCode = null;
    }
    
    public McpToolException(String toolName, String errorCode, String message) {
        super(message);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }
    
    public McpToolException(String toolName, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }
    
    public String getToolName() {
        return toolName;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("McpToolException");
        
        if (toolName != null) {
            sb.append(" [tool=").append(toolName).append("]");
        }
        
        if (errorCode != null) {
            sb.append(" [code=").append(errorCode).append("]");
        }
        
        sb.append(": ").append(getMessage());
        
        return sb.toString();
    }
}
