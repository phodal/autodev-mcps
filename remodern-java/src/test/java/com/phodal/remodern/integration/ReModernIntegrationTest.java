package com.phodal.remodern.integration;

import com.phodal.remodern.core.McpToolRegistry;
import com.phodal.remodern.core.McpToolResult;
import com.phodal.remodern.tools.bytecode.ByteCodeTool;
import com.phodal.remodern.tools.codegen.AstCodeGenTool;
import com.phodal.remodern.tools.codegen.TemplateCodeGenTool;
import com.phodal.remodern.tools.migration.OpenRewriteTool;
import com.phodal.remodern.tools.parsing.JSPParseTool;
import com.phodal.remodern.tools.parsing.JavaParseTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the complete ReModern Java toolchain
 */
class ReModernIntegrationTest {
    
    private McpToolRegistry registry;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        registry = new McpToolRegistry();
        
        // Register all tools
        registry.registerTool(new AstCodeGenTool());
        registry.registerTool(new TemplateCodeGenTool());
        registry.registerTool(new OpenRewriteTool());
        registry.registerTool(new JSPParseTool());
        registry.registerTool(new ByteCodeTool());
        registry.registerTool(new JavaParseTool());
    }
    
    @Test
    void shouldRegisterAllTools() {
        assertThat(registry.getToolCount()).isEqualTo(6);
        assertThat(registry.isToolRegistered("ast-code-gen")).isTrue();
        assertThat(registry.isToolRegistered("template-code-gen")).isTrue();
        assertThat(registry.isToolRegistered("openrewrite-tool")).isTrue();
        assertThat(registry.isToolRegistered("jsp-parse-tool")).isTrue();
        assertThat(registry.isToolRegistered("bytecode-tool")).isTrue();
        assertThat(registry.isToolRegistered("java-parse-tool")).isTrue();
    }
    
    @Test
    void shouldHaveCorrectCategories() {
        var categories = registry.getCategories();
        assertThat(categories).contains("code-generation", "migration", "parsing", "bytecode-analysis");
        
        var codeGenTools = registry.getToolsByCategory("code-generation");
        assertThat(codeGenTools).hasSize(2);
        assertThat(codeGenTools.stream().map(tool -> tool.getName()))
                .containsExactlyInAnyOrder("ast-code-gen", "template-code-gen");
        
        var parsingTools = registry.getToolsByCategory("parsing");
        assertThat(parsingTools).hasSize(2);
        assertThat(parsingTools.stream().map(tool -> tool.getName()))
                .containsExactlyInAnyOrder("jsp-parse-tool", "java-parse-tool");
    }
    
    @Test
    void shouldCompleteCodeGenerationAndParsingWorkflow() throws Exception {
        // Step 1: Generate a Java class using AST code generation
        Map<String, Object> codeGenParams = new HashMap<>();
        codeGenParams.put("type", "class");
        codeGenParams.put("name", "GeneratedClass");
        codeGenParams.put("packageName", "com.example.generated");
        codeGenParams.put("modifiers", List.of("public"));
        codeGenParams.put("outputDir", tempDir.toString());
        
        // Add a field
        Map<String, Object> field = Map.of(
            "name", "id",
            "type", "Long",
            "modifiers", List.of("private")
        );
        codeGenParams.put("fields", List.of(field));
        
        // Add a method
        Map<String, Object> method = Map.of(
            "name", "getId",
            "returnType", "Long",
            "modifiers", List.of("public"),
            "body", "return this.id"
        );
        codeGenParams.put("methods", List.of(method));
        
        McpToolResult codeGenResult = registry.getTool("ast-code-gen").execute(codeGenParams);
        assertThat(codeGenResult.isSuccess()).isTrue();
        
        // Verify the file was created
        String outputPath = (String) codeGenResult.getMetadata().get("outputPath");
        Path generatedFile = Path.of(outputPath);
        assertThat(Files.exists(generatedFile)).isTrue();
        
        // Step 2: Parse the generated Java file
        Map<String, Object> parseParams = new HashMap<>();
        parseParams.put("source", generatedFile.toString());
        parseParams.put("analysisType", "full");
        
        McpToolResult parseResult = registry.getTool("java-parse-tool").execute(parseParams);
        assertThat(parseResult.isSuccess()).isTrue();
        
        // Verify the parsing results
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) parseResult.getContent();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        assertThat(files).hasSize(1);
        
        Map<String, Object> fileAnalysis = files.get(0);
        assertThat(fileAnalysis.get("package")).isEqualTo("com.example.generated");
        
        // Verify class structure
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> types = (List<Map<String, Object>>) fileAnalysis.get("types");
        assertThat(types).hasSize(1);
        assertThat(types.get(0).get("name")).isEqualTo("GeneratedClass");
        
        // Verify fields and methods were parsed correctly
        assertThat(fileAnalysis.get("fieldCount")).isEqualTo(1);
        assertThat(fileAnalysis.get("methodCount")).isEqualTo(1);
    }
    
    @Test
    void shouldGenerateCodeUsingTemplate() throws Exception {
        // Create a simple FreeMarker template
        String template = """
            package ${packageName};
            
            public class ${className} {
                <#list fields as field>
                private ${field.type} ${field.name};
                </#list>
                
                <#list fields as field>
                public ${field.type} get${field.name?cap_first}() {
                    return ${field.name};
                }
                
                public void set${field.name?cap_first}(${field.type} ${field.name}) {
                    this.${field.name} = ${field.name};
                }
                </#list>
            }
            """;
        
        // Prepare data model
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("packageName", "com.example.template");
        dataModel.put("className", "TemplateClass");
        dataModel.put("fields", List.of(
            Map.of("name", "name", "type", "String"),
            Map.of("name", "age", "type", "Integer")
        ));
        
        // Generate code using template
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("template", template);
        templateParams.put("templateType", "content");
        templateParams.put("dataModel", dataModel);
        templateParams.put("outputFile", tempDir.resolve("TemplateClass.java").toString());
        
        McpToolResult templateResult = registry.getTool("template-code-gen").execute(templateParams);
        assertThat(templateResult.isSuccess()).isTrue();
        
        // Verify the generated file
        Path generatedFile = tempDir.resolve("TemplateClass.java");
        assertThat(Files.exists(generatedFile)).isTrue();
        
        String generatedContent = Files.readString(generatedFile);
        assertThat(generatedContent).contains("package com.example.template;");
        assertThat(generatedContent).contains("public class TemplateClass");
        assertThat(generatedContent).contains("private String name;");
        assertThat(generatedContent).contains("private Integer age;");
        assertThat(generatedContent).contains("public String getName()");
        assertThat(generatedContent).contains("public void setAge(Integer age)");
    }
    
    @Test
    void shouldParseJSPFile() throws Exception {
        // Create a sample JSP file
        String jspContent = """
            <%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
            <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
            <%@ include file="header.jsp" %>
            
            <!DOCTYPE html>
            <html>
            <head>
                <title>Test JSP</title>
            </head>
            <body>
                <h1>Welcome ${user.name}!</h1>
                
                <c:forEach items="${items}" var="item">
                    <p>${item.description}</p>
                </c:forEach>
                
                <%
                    String message = "Hello from scriptlet";
                    out.println(message);
                %>
                
                <%= new java.util.Date() %>
                
                <%-- This is a JSP comment --%>
            </body>
            </html>
            """;
        
        Path jspFile = tempDir.resolve("test.jsp");
        Files.writeString(jspFile, jspContent);
        
        // Parse the JSP file
        Map<String, Object> jspParams = new HashMap<>();
        jspParams.put("source", jspFile.toString());
        jspParams.put("analysisType", "full");
        
        McpToolResult jspResult = registry.getTool("jsp-parse-tool").execute(jspParams);
        assertThat(jspResult.isSuccess()).isTrue();
        
        // Verify the parsing results
        @SuppressWarnings("unchecked")
        Map<String, Object> analysisResult = (Map<String, Object>) jspResult.getContent();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) analysisResult.get("files");
        assertThat(files).hasSize(1);
        
        Map<String, Object> fileAnalysis = files.get(0);
        
        // Verify directives
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> directives = (List<Map<String, Object>>) fileAnalysis.get("directives");
        assertThat(directives).hasSizeGreaterThan(0);
        
        // Verify taglibs
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> taglibs = (List<Map<String, Object>>) fileAnalysis.get("taglibs");
        assertThat(taglibs).hasSize(1);
        assertThat(taglibs.get(0).get("prefix")).isEqualTo("c");
        
        // Verify includes
        @SuppressWarnings("unchecked")
        List<String> includes = (List<String>) fileAnalysis.get("includes");
        assertThat(includes).contains("header.jsp");
        
        // Verify expressions
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elExpressions = (List<Map<String, Object>>) fileAnalysis.get("elExpressions");
        assertThat(elExpressions).hasSizeGreaterThan(0);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jspExpressions = (List<Map<String, Object>>) fileAnalysis.get("jspExpressions");
        assertThat(jspExpressions).hasSizeGreaterThan(0);
    }
    
    @Test
    void shouldSupportOperationQueries() {
        // Test code generation operations
        var codeGenTools = registry.getToolsSupportingOperation("code-generation");
        assertThat(codeGenTools).hasSize(2);
        assertThat(codeGenTools.stream().map(tool -> tool.getName()))
                .containsExactlyInAnyOrder("ast-code-gen", "template-code-gen");
        
        // Test parsing operations
        var parsingTools = registry.getToolsSupportingOperation("java-parse");
        assertThat(parsingTools).hasSize(1);
        assertThat(parsingTools.get(0).getName()).isEqualTo("java-parse-tool");
        
        // Test migration operations
        var migrationTools = registry.getToolsSupportingOperation("migrate");
        assertThat(migrationTools).hasSize(1);
        assertThat(migrationTools.get(0).getName()).isEqualTo("openrewrite-tool");
    }
    
    @Test
    void shouldHandleToolNotFound() {
        assertThat(registry.getTool("nonexistent-tool")).isNull();
        assertThat(registry.isToolRegistered("nonexistent-tool")).isFalse();
    }
    
    @Test
    void shouldProvideToolSchemas() {
        for (var tool : registry.getAllTools()) {
            var schema = tool.getInputSchema();
            assertThat(schema).isNotNull();
            assertThat(schema.has("type")).isTrue();
            assertThat(schema.has("properties")).isTrue();
            assertThat(schema.get("type").asText()).isEqualTo("object");
        }
    }
}
