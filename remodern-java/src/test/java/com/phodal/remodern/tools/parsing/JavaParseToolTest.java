package com.phodal.remodern.tools.parsing;

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

class JavaParseToolTest {
    
    private JavaParseTool tool;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        tool = new JavaParseTool();
    }
    
    @Test
    void shouldHaveCorrectToolInfo() {
        assertThat(tool.getName()).isEqualTo("java-parse-tool");
        assertThat(tool.getCategory()).isEqualTo("parsing");
        assertThat(tool.getDescription()).contains("JavaParser");
    }
    
    @Test
    void shouldSupportParsingOperations() {
        assertThat(tool.supportsOperation("java-parse")).isTrue();
        assertThat(tool.supportsOperation("source-analysis")).isTrue();
        assertThat(tool.supportsOperation("ast-analysis")).isTrue();
        assertThat(tool.supportsOperation("java-structure")).isTrue();
        assertThat(tool.supportsOperation("unsupported")).isFalse();
    }
    
    @Test
    void shouldParseSimpleJavaClass() throws Exception {
        // Given
        String javaCode = """
            package com.example;
            
            import java.util.List;
            
            public class TestClass {
                private String name;
                
                public String getName() {
                    return name;
                }
                
                public void setName(String name) {
                    this.name = name;
                }
            }
            """;
        
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.writeString(javaFile, javaCode);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", javaFile.toString());
        parameters.put("analysisType", "full");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) result.getContent();
        assertThat(analysisResult).containsKey("files");
        assertThat(analysisResult).containsKey("summary");
        assertThat(analysisResult.get("totalFiles")).isEqualTo(1);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        assertThat(files).hasSize(1);
        
        Map<String, Object> fileAnalysis = files.get(0);
        assertThat(fileAnalysis).containsKey("file");
        assertThat(fileAnalysis).containsKey("package");
        assertThat(fileAnalysis.get("package")).isEqualTo("com.example");
    }
    
    @Test
    void shouldAnalyzeClassStructure() throws Exception {
        // Given
        String javaCode = """
            package com.example;
            
            import java.io.Serializable;
            
            @Entity
            public class User implements Serializable {
                private Long id;
                private String name;
            }
            """;
        
        Path javaFile = tempDir.resolve("User.java");
        Files.writeString(javaFile, javaCode);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", javaFile.toString());
        parameters.put("analysisType", "structure");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) result.getContent();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        Map<String, Object> fileAnalysis = files.get(0);
        
        assertThat(fileAnalysis).containsKey("types");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> types = (List<Map<String, Object>>) fileAnalysis.get("types");
        assertThat(types).hasSize(1);
        
        Map<String, Object> typeInfo = types.get(0);
        assertThat(typeInfo.get("name")).isEqualTo("User");
        assertThat(typeInfo.get("kind")).isEqualTo("class");
        
        @SuppressWarnings("unchecked")
        List<String> modifiers = (List<String>) typeInfo.get("modifiers");
        assertThat(modifiers).contains("public");
        
        @SuppressWarnings("unchecked")
        List<String> implementedTypes = (List<String>) typeInfo.get("implementedTypes");
        assertThat(implementedTypes).contains("Serializable");
        
        @SuppressWarnings("unchecked")
        List<String> annotations = (List<String>) typeInfo.get("annotations");
        assertThat(annotations).contains("@Entity");
    }
    
    @Test
    void shouldAnalyzeMethods() throws Exception {
        // Given
        String javaCode = """
            package com.example;
            
            public class Calculator {
                @Override
                public String toString() {
                    return "Calculator";
                }
                
                public int add(int a, int b) throws IllegalArgumentException {
                    if (a < 0 || b < 0) {
                        throw new IllegalArgumentException("Negative numbers not allowed");
                    }
                    return a + b;
                }
                
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            """;
        
        Path javaFile = tempDir.resolve("Calculator.java");
        Files.writeString(javaFile, javaCode);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", javaFile.toString());
        parameters.put("analysisType", "methods");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) result.getContent();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        Map<String, Object> fileAnalysis = files.get(0);
        
        assertThat(fileAnalysis).containsKey("methods");
        assertThat(fileAnalysis.get("methodCount")).isEqualTo(3);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) fileAnalysis.get("methods");
        assertThat(methods).hasSize(3);
        
        // Find the add method
        Map<String, Object> addMethod = methods.stream()
                .filter(m -> "add".equals(m.get("name")))
                .findFirst()
                .orElseThrow();
        
        assertThat(addMethod.get("returnType")).isEqualTo("int");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters2 = (List<Map<String, Object>>) addMethod.get("parameters");
        assertThat(parameters2).hasSize(2);
        assertThat(parameters2.get(0).get("name")).isEqualTo("a");
        assertThat(parameters2.get(0).get("type")).isEqualTo("int");
        
        @SuppressWarnings("unchecked")
        List<String> thrownExceptions = (List<String>) addMethod.get("thrownExceptions");
        assertThat(thrownExceptions).contains("IllegalArgumentException");
    }
    
    @Test
    void shouldAnalyzeFields() throws Exception {
        // Given
        String javaCode = """
            package com.example;
            
            public class Person {
                @Id
                private Long id = null;
                
                private static final String DEFAULT_NAME = "Unknown";
                
                public String name;
                
                protected int age;
            }
            """;
        
        Path javaFile = tempDir.resolve("Person.java");
        Files.writeString(javaFile, javaCode);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", javaFile.toString());
        parameters.put("analysisType", "fields");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) result.getContent();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        Map<String, Object> fileAnalysis = files.get(0);
        
        assertThat(fileAnalysis).containsKey("fields");
        assertThat(fileAnalysis.get("fieldCount")).isEqualTo(4);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) fileAnalysis.get("fields");
        assertThat(fields).hasSize(4);
        
        // Find the id field
        Map<String, Object> idField = fields.stream()
                .filter(f -> "id".equals(f.get("name")))
                .findFirst()
                .orElseThrow();
        
        assertThat(idField.get("type")).isEqualTo("Long");
        
        @SuppressWarnings("unchecked")
        List<String> modifiers = (List<String>) idField.get("modifiers");
        assertThat(modifiers).contains("private");
        
        @SuppressWarnings("unchecked")
        List<String> annotations = (List<String>) idField.get("annotations");
        assertThat(annotations).contains("@Id");
        
        assertThat(idField.get("initializer")).isEqualTo("null");
    }
    
    @Test
    void shouldAnalyzeImports() throws Exception {
        // Given
        String javaCode = """
            package com.example;
            
            import java.util.List;
            import java.util.Map;
            import static java.util.Collections.emptyList;
            import static org.junit.jupiter.api.Assertions.*;
            
            public class TestClass {
            }
            """;
        
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.writeString(javaFile, javaCode);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", javaFile.toString());
        parameters.put("analysisType", "imports");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) result.getContent();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        Map<String, Object> fileAnalysis = files.get(0);
        
        assertThat(fileAnalysis).containsKey("imports");
        assertThat(fileAnalysis).containsKey("staticImports");
        
        @SuppressWarnings("unchecked")
        List<String> imports = (List<String>) fileAnalysis.get("imports");
        assertThat(imports).contains("java.util.List", "java.util.Map");
        
        @SuppressWarnings("unchecked")
        List<String> staticImports = (List<String>) fileAnalysis.get("staticImports");
        assertThat(staticImports).contains("java.util.Collections.emptyList", "org.junit.jupiter.api.Assertions.*");
    }
    
    @Test
    void shouldAnalyzeDirectory() throws Exception {
        // Given
        String class1Code = """
            package com.example;
            public class Class1 {
                private String field1;
            }
            """;
        
        String class2Code = """
            package com.example;
            public class Class2 {
                private int field2;
                public void method1() {}
            }
            """;
        
        Path javaFile1 = tempDir.resolve("Class1.java");
        Path javaFile2 = tempDir.resolve("Class2.java");
        Files.writeString(javaFile1, class1Code);
        Files.writeString(javaFile2, class2Code);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", tempDir.toString());
        parameters.put("analysisType", "full");
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) result.getContent();
        assertThat(analysisResult.get("totalFiles")).isEqualTo(2);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        assertThat(files).hasSize(2);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) analysisResult.get("summary");
        assertThat(summary.get("totalTypes")).isEqualTo(2);
        assertThat(summary.get("totalFields")).isEqualTo(2);
        assertThat(summary.get("totalMethods")).isEqualTo(1);
    }
    
    @Test
    void shouldThrowExceptionForNonExistentSource() {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", "/nonexistent/path");
        
        // When & Then
        assertThatThrownBy(() -> tool.execute(parameters))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Source path not found");
    }
    
    @Test
    void shouldThrowExceptionForMissingRequiredParameters() {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        // Missing required 'source' parameter
        
        // When & Then
        assertThatThrownBy(() -> tool.execute(parameters))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Required parameter 'source' is missing");
    }
    
    @Test
    void shouldHandleParseErrors() throws Exception {
        // Given
        String invalidJavaCode = """
            package com.example;
            
            public class InvalidClass {
                // Missing closing brace
            """;
        
        Path javaFile = tempDir.resolve("InvalidClass.java");
        Files.writeString(javaFile, invalidJavaCode);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", javaFile.toString());
        
        // When
        McpToolResult result = tool.execute(parameters);
        
        // Then
        assertThat(result.isSuccess()).isTrue(); // Tool should still succeed but report parse errors
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) result.getContent();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        Map<String, Object> fileAnalysis = files.get(0);
        
        assertThat(fileAnalysis).containsKey("parseErrors");
    }
}
