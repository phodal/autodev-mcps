package com.phodal.remodern.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpToolRegistryTest {
    
    private McpToolRegistry registry;
    
    @Mock
    private McpTool mockTool1;
    
    @Mock
    private McpTool mockTool2;
    
    @Mock
    private McpTool mockTool3;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new McpToolRegistry();
        
        when(mockTool1.getName()).thenReturn("tool1");
        when(mockTool1.getCategory()).thenReturn("category1");
        when(mockTool1.getDescription()).thenReturn("Tool 1 description");
        
        when(mockTool2.getName()).thenReturn("tool2");
        when(mockTool2.getCategory()).thenReturn("category1");
        when(mockTool2.getDescription()).thenReturn("Tool 2 description");
        
        when(mockTool3.getName()).thenReturn("tool3");
        when(mockTool3.getCategory()).thenReturn("category2");
        when(mockTool3.getDescription()).thenReturn("Tool 3 description");
    }
    
    @Test
    void shouldRegisterTool() {
        // When
        registry.registerTool(mockTool1);
        
        // Then
        assertThat(registry.isToolRegistered("tool1")).isTrue();
        assertThat(registry.getTool("tool1")).isEqualTo(mockTool1);
        assertThat(registry.getToolCount()).isEqualTo(1);
    }
    
    @Test
    void shouldThrowExceptionWhenRegisteringDuplicateTool() {
        // Given
        registry.registerTool(mockTool1);
        
        // When & Then
        assertThatThrownBy(() -> registry.registerTool(mockTool1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tool with name 'tool1' is already registered");
    }
    
    @Test
    void shouldUnregisterTool() {
        // Given
        registry.registerTool(mockTool1);
        
        // When
        boolean result = registry.unregisterTool("tool1");
        
        // Then
        assertThat(result).isTrue();
        assertThat(registry.isToolRegistered("tool1")).isFalse();
        assertThat(registry.getTool("tool1")).isNull();
        assertThat(registry.getToolCount()).isEqualTo(0);
    }
    
    @Test
    void shouldReturnFalseWhenUnregisteringNonExistentTool() {
        // When
        boolean result = registry.unregisterTool("nonexistent");
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void shouldGetAllTools() {
        // Given
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2);
        registry.registerTool(mockTool3);
        
        // When
        Collection<McpTool> allTools = registry.getAllTools();
        
        // Then
        assertThat(allTools).hasSize(3);
        assertThat(allTools).containsExactlyInAnyOrder(mockTool1, mockTool2, mockTool3);
    }
    
    @Test
    void shouldGetToolNames() {
        // Given
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2);
        
        // When
        Set<String> toolNames = registry.getToolNames();
        
        // Then
        assertThat(toolNames).containsExactlyInAnyOrder("tool1", "tool2");
    }
    
    @Test
    void shouldGetToolsByCategory() {
        // Given
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2);
        registry.registerTool(mockTool3);
        
        // When
        List<McpTool> category1Tools = registry.getToolsByCategory("category1");
        List<McpTool> category2Tools = registry.getToolsByCategory("category2");
        List<McpTool> nonExistentCategoryTools = registry.getToolsByCategory("nonexistent");
        
        // Then
        assertThat(category1Tools).hasSize(2);
        assertThat(category1Tools).containsExactlyInAnyOrder(mockTool1, mockTool2);
        
        assertThat(category2Tools).hasSize(1);
        assertThat(category2Tools).containsExactly(mockTool3);
        
        assertThat(nonExistentCategoryTools).isEmpty();
    }
    
    @Test
    void shouldGetCategories() {
        // Given
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2);
        registry.registerTool(mockTool3);
        
        // When
        Set<String> categories = registry.getCategories();
        
        // Then
        assertThat(categories).containsExactlyInAnyOrder("category1", "category2");
    }
    
    @Test
    void shouldGetToolsSupportingOperation() {
        // Given
        when(mockTool1.supportsOperation("operation1")).thenReturn(true);
        when(mockTool1.supportsOperation("operation2")).thenReturn(false);
        when(mockTool2.supportsOperation("operation1")).thenReturn(false);
        when(mockTool2.supportsOperation("operation2")).thenReturn(true);
        when(mockTool3.supportsOperation("operation1")).thenReturn(true);
        when(mockTool3.supportsOperation("operation2")).thenReturn(true);
        
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2);
        registry.registerTool(mockTool3);
        
        // When
        List<McpTool> operation1Tools = registry.getToolsSupportingOperation("operation1");
        List<McpTool> operation2Tools = registry.getToolsSupportingOperation("operation2");
        
        // Then
        assertThat(operation1Tools).containsExactlyInAnyOrder(mockTool1, mockTool3);
        assertThat(operation2Tools).containsExactlyInAnyOrder(mockTool2, mockTool3);
    }
    
    @Test
    void shouldClearAllTools() {
        // Given
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2);
        registry.registerTool(mockTool3);
        
        // When
        registry.clear();
        
        // Then
        assertThat(registry.getToolCount()).isEqualTo(0);
        assertThat(registry.getAllTools()).isEmpty();
        assertThat(registry.getCategories()).isEmpty();
    }
    
    @Test
    void shouldRemoveToolFromCategoryWhenUnregistering() {
        // Given
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2); // Both in category1
        
        // When
        registry.unregisterTool("tool1");
        
        // Then
        List<McpTool> category1Tools = registry.getToolsByCategory("category1");
        assertThat(category1Tools).hasSize(1);
        assertThat(category1Tools).containsExactly(mockTool2);
    }
    
    @Test
    void shouldRemoveCategoryWhenLastToolUnregistered() {
        // Given
        registry.registerTool(mockTool3); // Only tool in category2
        
        // When
        registry.unregisterTool("tool3");
        
        // Then
        Set<String> categories = registry.getCategories();
        assertThat(categories).doesNotContain("category2");
    }
}
