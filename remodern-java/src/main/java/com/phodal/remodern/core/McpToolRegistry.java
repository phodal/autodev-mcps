package com.phodal.remodern.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing MCP tools
 */
public class McpToolRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(McpToolRegistry.class);
    
    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> categorizedTools = new ConcurrentHashMap<>();
    
    /**
     * Register a tool
     * @param tool the tool to register
     * @throws IllegalArgumentException if tool name is already registered
     */
    public void registerTool(McpTool tool) {
        String name = tool.getName();
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Tool with name '" + name + "' is already registered");
        }
        
        tools.put(name, tool);
        
        // Add to category index
        String category = tool.getCategory();
        categorizedTools.computeIfAbsent(category, k -> new HashSet<>()).add(name);
        
        logger.info("Registered tool '{}' in category '{}'", name, category);
    }
    
    /**
     * Unregister a tool
     * @param toolName name of the tool to unregister
     * @return true if tool was removed, false if not found
     */
    public boolean unregisterTool(String toolName) {
        McpTool removed = tools.remove(toolName);
        if (removed != null) {
            // Remove from category index
            String category = removed.getCategory();
            Set<String> categoryTools = categorizedTools.get(category);
            if (categoryTools != null) {
                categoryTools.remove(toolName);
                if (categoryTools.isEmpty()) {
                    categorizedTools.remove(category);
                }
            }
            
            logger.info("Unregistered tool '{}'", toolName);
            return true;
        }
        return false;
    }
    
    /**
     * Get a tool by name
     * @param toolName tool name
     * @return the tool, or null if not found
     */
    public McpTool getTool(String toolName) {
        return tools.get(toolName);
    }
    
    /**
     * Get all registered tools
     * @return unmodifiable collection of all tools
     */
    public Collection<McpTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }
    
    /**
     * Get all tool names
     * @return unmodifiable set of tool names
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
    
    /**
     * Get tools by category
     * @param category tool category
     * @return list of tools in the category
     */
    public List<McpTool> getToolsByCategory(String category) {
        Set<String> toolNames = categorizedTools.get(category);
        if (toolNames == null) {
            return Collections.emptyList();
        }
        
        return toolNames.stream()
                .map(tools::get)
                .filter(Objects::nonNull)
                .toList();
    }
    
    /**
     * Get all categories
     * @return unmodifiable set of categories
     */
    public Set<String> getCategories() {
        return Collections.unmodifiableSet(categorizedTools.keySet());
    }
    
    /**
     * Check if a tool is registered
     * @param toolName tool name
     * @return true if registered, false otherwise
     */
    public boolean isToolRegistered(String toolName) {
        return tools.containsKey(toolName);
    }
    
    /**
     * Get the number of registered tools
     * @return number of tools
     */
    public int getToolCount() {
        return tools.size();
    }
    
    /**
     * Clear all registered tools
     */
    public void clear() {
        tools.clear();
        categorizedTools.clear();
        logger.info("Cleared all registered tools");
    }
    
    /**
     * Get tools that support a specific operation
     * @param operation operation name
     * @return list of tools that support the operation
     */
    public List<McpTool> getToolsSupportingOperation(String operation) {
        return tools.values().stream()
                .filter(tool -> tool.supportsOperation(operation))
                .toList();
    }
}
