package com.phodal.remodern.tools.codegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phodal.remodern.core.AbstractMcpTool;
import com.phodal.remodern.core.McpToolException;
import com.phodal.remodern.core.McpToolResult;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP Tool for generating code using FreeMarker templates
 */
public class TemplateCodeGenTool extends AbstractMcpTool {
    
    private final Configuration freemarkerConfig;
    
    public TemplateCodeGenTool() {
        super("template-code-gen", 
              "Generate code using FreeMarker templates. Supports custom templates with data models.",
              "code-generation");
        
        // Initialize FreeMarker configuration
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
        this.freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        this.freemarkerConfig.setLogTemplateExceptions(false);
        this.freemarkerConfig.setWrapUncheckedExceptions(true);
        this.freemarkerConfig.setFallbackOnNullLoopVariable(false);
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = createBaseSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        ArrayNode required = (ArrayNode) schema.get("required");
        
        // Template content or path
        ObjectNode templateProperty = objectMapper.createObjectNode();
        templateProperty.put("type", "string");
        templateProperty.put("description", "FreeMarker template content or path to template file");
        properties.set("template", templateProperty);
        required.add("template");
        
        // Template type
        ObjectNode templateTypeProperty = objectMapper.createObjectNode();
        templateTypeProperty.put("type", "string");
        templateTypeProperty.put("description", "Whether template is content or file path");
        templateTypeProperty.put("default", "content");
        ArrayNode templateTypeEnum = objectMapper.createArrayNode();
        templateTypeEnum.add("content").add("file");
        templateTypeProperty.set("enum", templateTypeEnum);
        properties.set("templateType", templateTypeProperty);
        
        // Data model
        ObjectNode dataModelProperty = objectMapper.createObjectNode();
        dataModelProperty.put("type", "object");
        dataModelProperty.put("description", "Data model to be used in template processing");
        properties.set("dataModel", dataModelProperty);
        required.add("dataModel");
        
        // Output file path
        ObjectNode outputFileProperty = objectMapper.createObjectNode();
        outputFileProperty.put("type", "string");
        outputFileProperty.put("description", "Output file path for generated code");
        properties.set("outputFile", outputFileProperty);
        
        // Template directory (for file-based templates)
        ObjectNode templateDirProperty = objectMapper.createObjectNode();
        templateDirProperty.put("type", "string");
        templateDirProperty.put("description", "Directory containing template files");
        templateDirProperty.put("default", "templates");
        properties.set("templateDir", templateDirProperty);
        
        // Encoding
        ObjectNode encodingProperty = objectMapper.createObjectNode();
        encodingProperty.put("type", "string");
        encodingProperty.put("description", "Character encoding for template and output");
        encodingProperty.put("default", "UTF-8");
        properties.set("encoding", encodingProperty);
        
        return schema;
    }
    
    @Override
    protected McpToolResult doExecute(Map<String, Object> parameters) throws McpToolException {
        String templateContent = getRequiredParameter(parameters, "template", String.class);
        String templateType = getOptionalParameter(parameters, "templateType", "content", String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> dataModel = getRequiredParameter(parameters, "dataModel", Map.class);
        String outputFile = getOptionalParameter(parameters, "outputFile", null, String.class);
        String templateDir = getOptionalParameter(parameters, "templateDir", "templates", String.class);
        String encoding = getOptionalParameter(parameters, "encoding", "UTF-8", String.class);
        
        validateNotEmpty(templateContent, "template");
        
        try {
            Template template;
            
            if ("file".equals(templateType)) {
                // Load template from file
                template = loadTemplateFromFile(templateContent, templateDir);
            } else {
                // Use template content directly
                template = createTemplateFromString(templateContent);
            }
            
            // Process template with data model
            String generatedCode = processTemplate(template, dataModel);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("generatedCode", generatedCode);
            metadata.put("templateType", templateType);
            metadata.put("dataModelKeys", dataModel.keySet());
            
            // Write to output file if specified
            if (outputFile != null && !outputFile.trim().isEmpty()) {
                writeToFile(generatedCode, outputFile, encoding);
                metadata.put("outputFile", outputFile);
            }
            
            return McpToolResult.success("Code generated successfully from template", metadata);
            
        } catch (IOException e) {
            throw new McpToolException(getName(), "IO_ERROR", 
                    "Failed to read template or write output", e);
        } catch (TemplateException e) {
            throw new McpToolException(getName(), "TEMPLATE_ERROR", 
                    "Template processing failed: " + e.getMessage(), e);
        }
    }
    
    private Template loadTemplateFromFile(String templatePath, String templateDir) 
            throws IOException, McpToolException {
        try {
            // Set template directory
            Path templateDirPath = Paths.get(templateDir);
            if (!Files.exists(templateDirPath)) {
                throw new McpToolException(getName(), "TEMPLATE_DIR_NOT_FOUND", 
                        "Template directory not found: " + templateDir);
            }
            
            freemarkerConfig.setDirectoryForTemplateLoading(templateDirPath.toFile());
            
            // Load template
            return freemarkerConfig.getTemplate(templatePath);
            
        } catch (IOException e) {
            throw new McpToolException(getName(), "TEMPLATE_LOAD_ERROR", 
                    "Failed to load template file: " + templatePath, e);
        }
    }
    
    private Template createTemplateFromString(String templateContent) 
            throws IOException, McpToolException {
        try {
            return new Template("inline-template", new StringReader(templateContent), freemarkerConfig);
        } catch (IOException e) {
            throw new McpToolException(getName(), "TEMPLATE_PARSE_ERROR", 
                    "Failed to parse template content", e);
        }
    }
    
    private String processTemplate(Template template, Map<String, Object> dataModel) 
            throws TemplateException, IOException, McpToolException {
        try (StringWriter writer = new StringWriter()) {
            template.process(dataModel, writer);
            return writer.toString();
        } catch (TemplateException e) {
            throw new McpToolException(getName(), "TEMPLATE_PROCESSING_ERROR", 
                    "Template processing failed: " + e.getMessage(), e);
        }
    }
    
    private void writeToFile(String content, String outputFile, String encoding) 
            throws IOException, McpToolException {
        try {
            Path outputPath = Paths.get(outputFile);
            
            // Create parent directories if they don't exist
            Path parentDir = outputPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // Write content to file
            Files.writeString(outputPath, content, java.nio.charset.Charset.forName(encoding));
            
        } catch (IOException e) {
            throw new McpToolException(getName(), "FILE_WRITE_ERROR", 
                    "Failed to write output to file: " + outputFile, e);
        }
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        return switch (operation.toLowerCase()) {
            case "template-generation", "freemarker-generation", "code-generation" -> true;
            default -> false;
        };
    }
}
