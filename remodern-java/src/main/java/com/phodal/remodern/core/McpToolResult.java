package com.phodal.remodern.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents the result of executing an MCP tool
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolResult {
    
    @JsonProperty("success")
    private final boolean success;
    
    @JsonProperty("content")
    private final Object content;
    
    @JsonProperty("error")
    private final String error;
    
    @JsonProperty("metadata")
    private final Map<String, Object> metadata;
    
    @JsonProperty("timestamp")
    private final Instant timestamp;
    
    private McpToolResult(boolean success, Object content, String error, Map<String, Object> metadata) {
        this.success = success;
        this.content = content;
        this.error = error;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.timestamp = Instant.now();
    }
    
    /**
     * Create a successful result
     * @param content the result content
     * @return successful result
     */
    public static McpToolResult success(Object content) {
        return new McpToolResult(true, content, null, null);
    }
    
    /**
     * Create a successful result with metadata
     * @param content the result content
     * @param metadata additional metadata
     * @return successful result
     */
    public static McpToolResult success(Object content, Map<String, Object> metadata) {
        return new McpToolResult(true, content, null, metadata);
    }
    
    /**
     * Create a failure result
     * @param error error message
     * @return failure result
     */
    public static McpToolResult failure(String error) {
        return new McpToolResult(false, null, error, null);
    }
    
    /**
     * Create a failure result with metadata
     * @param error error message
     * @param metadata additional metadata
     * @return failure result
     */
    public static McpToolResult failure(String error, Map<String, Object> metadata) {
        return new McpToolResult(false, null, error, metadata);
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public Object getContent() {
        return content;
    }
    
    public String getError() {
        return error;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Add metadata to the result
     * @param key metadata key
     * @param value metadata value
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    /**
     * Get typed content
     * @param clazz target class
     * @param <T> target type
     * @return typed content
     * @throws ClassCastException if content cannot be cast to target type
     */
    @SuppressWarnings("unchecked")
    public <T> T getContentAs(Class<T> clazz) {
        if (content == null) {
            return null;
        }
        return (T) content;
    }
    
    @Override
    public String toString() {
        return "McpToolResult{" +
                "success=" + success +
                ", content=" + content +
                ", error='" + error + '\'' +
                ", metadata=" + metadata +
                ", timestamp=" + timestamp +
                '}';
    }
}
