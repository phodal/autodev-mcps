package com.phodal.remodern.tools.codegen;

import com.phodal.remodern.core.McpToolException;
import com.phodal.remodern.core.McpToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AstCodeGenToolTest {
    
    private AstCodeGenTool tool;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        tool = new AstCodeGenTool();
    }
    
    @Test
    void shouldHaveCorrectToolInfo() {
        assertThat(tool.getName()).isEqualTo("ast-code-gen");
        assertThat(tool.getCategory()).isEqualTo("code-generation");
        assertThat(tool.getDescription()).contains("JavaPoet");
    }
    
    @Test
    void shouldSupportCodeGenerationOperations() {
        assertThat(tool.supportsOperation("generate-class")).isTrue();
        assertThat(tool.supportsOperation("generate-interface")).isTrue();
        assertThat(tool.supportsOperation("generate-enum")).isTrue();
        assertThat(tool.supportsOperation("generate-method")).isTrue();
        assertThat(tool.supportsOperation("generate-field")).isTrue();
        assertThat(tool.supportsOperation("code-generation")).isTrue();
        assertThat(tool.supportsOperation("unsupported")).isFalse();
    }
    
    @Test
    void shouldGenerateSimpleClass() throws McpToolException {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "class");
        parameters.put("name", "TestClass");
        parameters.put("packageName", "com.example");
        parameters.put("modifiers", List.of("public"));
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMetadata()).containsKey("generatedCode");
        assertThat(result.getMetadata()).containsKey("className");
        assertThat(result.getMetadata()).containsKey("packageName");
        
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("package com.example;");
        assertThat(generatedCode).contains("public class TestClass");
    }
    
    @Test
    void shouldGenerateClassWithSuperclass() throws McpToolException {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "class");
        parameters.put("name", "ChildClass");
        parameters.put("packageName", "com.example");
        parameters.put("modifiers", List.of("public"));
        parameters.put("superclass", "ParentClass");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("extends ParentClass");
    }
    
    @Test
    void shouldGenerateClassWithInterfaces() throws McpToolException {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "class");
        parameters.put("name", "TestClass");
        parameters.put("packageName", "com.example");
        parameters.put("modifiers", List.of("public"));
        parameters.put("interfaces", List.of("Serializable", "Comparable"));
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("implements Serializable, Comparable");
    }
    
    @Test
    void shouldGenerateClassWithFields() throws McpToolException {
        // Given
        Map<String, Object> fields = Map.of(
            "name", "id",
            "type", "Long",
            "modifiers", List.of("private"),
            "initializer", "null"
        );
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "class");
        parameters.put("name", "TestClass");
        parameters.put("packageName", "com.example");
        parameters.put("modifiers", List.of("public"));
        parameters.put("fields", List.of(fields));
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("private Long id = null;");
    }
    
    @Test
    void shouldGenerateClassWithMethods() throws McpToolException {
        // Given
        Map<String, Object> method = Map.of(
            "name", "getId",
            "returnType", "Long",
            "modifiers", List.of("public"),
            "body", "return this.id"
        );
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "class");
        parameters.put("name", "TestClass");
        parameters.put("packageName", "com.example");
        parameters.put("modifiers", List.of("public"));
        parameters.put("methods", List.of(method));
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("public Long getId()");
        assertThat(generatedCode).contains("return this.id;");
    }
    
    @Test
    void shouldGenerateInterface() throws McpToolException {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "interface");
        parameters.put("name", "TestInterface");
        parameters.put("packageName", "com.example");
        parameters.put("modifiers", List.of("public"));
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("public interface TestInterface");
    }
    
    @Test
    void shouldGenerateEnum() throws McpToolException {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "enum");
        parameters.put("name", "TestEnum");
        parameters.put("packageName", "com.example");
        parameters.put("modifiers", List.of("public"));
        parameters.put("constants", List.of("VALUE1", "VALUE2", "VALUE3"));
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("public enum TestEnum");
        assertThat(generatedCode).contains("VALUE1");
        assertThat(generatedCode).contains("VALUE2");
        assertThat(generatedCode).contains("VALUE3");
    }
    
    @Test
    void shouldGenerateMethod() throws McpToolException {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "method");
        parameters.put("name", "testMethod");
        parameters.put("returnType", "String");
        parameters.put("modifiers", List.of("public", "static"));
        parameters.put("body", "return \"test\"");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("public static String testMethod()");
        assertThat(generatedCode).contains("return \"test\";");
    }
    
    @Test
    void shouldGenerateField() throws McpToolException {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "field");
        parameters.put("name", "testField");
        parameters.put("fieldType", "String");
        parameters.put("modifiers", List.of("private", "final"));
        parameters.put("initializer", "\"default\"");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        String generatedCode = (String) result.getMetadata().get("generatedCode");
        assertThat(generatedCode).contains("private final String testField = \"default\";");
    }
    
    @Test
    void shouldWriteToFileWhenOutputDirSpecified() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "class");
        parameters.put("name", "TestClass");
        parameters.put("packageName", "com.example");
        parameters.put("outputDir", tempDir.toString());
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMetadata()).containsKey("outputPath");
        
        String outputPath = (String) result.getMetadata().get("outputPath");
        Path generatedFile = Path.of(outputPath);
        assertThat(Files.exists(generatedFile)).isTrue();
        
        String fileContent = Files.readString(generatedFile);
        assertThat(fileContent).contains("package com.example;");
        assertThat(fileContent).contains("class TestClass");
    }
    
    @Test
    void shouldThrowExceptionForMissingRequiredParameters() {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        // Missing required 'type' parameter
        
        // When & Then
        assertThatThrownBy(() -> tool.execute(parameters))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Required parameter 'type' is missing");
    }
    
    @Test
    void shouldThrowExceptionForInvalidType() {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "invalid");
        parameters.put("name", "TestClass");
        
        // When & Then
        assertThatThrownBy(() -> tool.execute(parameters))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Unsupported code generation type: invalid");
    }
    
    @Test
    void shouldThrowExceptionForEmptyName() {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "class");
        parameters.put("name", "");
        
        // When & Then
        assertThatThrownBy(() -> tool.execute(parameters))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Parameter 'name' cannot be empty");
    }
    
    @Test
    void shouldThrowExceptionForInvalidModifier() {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "class");
        parameters.put("name", "TestClass");
        parameters.put("modifiers", List.of("invalid"));
        
        // When & Then
        assertThatThrownBy(() -> tool.execute(parameters))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Unknown modifier: invalid");
    }
}
