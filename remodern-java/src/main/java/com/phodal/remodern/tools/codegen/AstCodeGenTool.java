package com.phodal.remodern.tools.codegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phodal.remodern.core.AbstractMcpTool;
import com.phodal.remodern.core.McpToolException;
import com.phodal.remodern.core.McpToolResult;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * MCP Tool for generating Java code using JavaPoet AST manipulation
 */
public class AstCodeGenTool extends AbstractMcpTool {
    
    public AstCodeGenTool() {
        super("ast-code-gen", 
              "Generate Java code using JavaPoet AST manipulation. Supports creating classes, interfaces, methods, fields, and more.",
              "code-generation");
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = createBaseSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        ArrayNode required = (ArrayNode) schema.get("required");
        
        // Type of code element to generate
        ObjectNode typeProperty = objectMapper.createObjectNode();
        typeProperty.put("type", "string");
        typeProperty.put("description", "Type of code element to generate");
        ArrayNode typeEnum = objectMapper.createArrayNode();
        typeEnum.add("class").add("interface").add("enum").add("method").add("field");
        typeProperty.set("enum", typeEnum);
        properties.set("type", typeProperty);
        required.add("type");
        
        // Name of the element
        ObjectNode nameProperty = objectMapper.createObjectNode();
        nameProperty.put("type", "string");
        nameProperty.put("description", "Name of the code element");
        properties.set("name", nameProperty);
        required.add("name");
        
        // Package name
        ObjectNode packageProperty = objectMapper.createObjectNode();
        packageProperty.put("type", "string");
        packageProperty.put("description", "Package name for the generated code");
        properties.set("packageName", packageProperty);
        
        // Output directory
        ObjectNode outputDirProperty = objectMapper.createObjectNode();
        outputDirProperty.put("type", "string");
        outputDirProperty.put("description", "Output directory for generated files");
        outputDirProperty.put("default", "src/main/java");
        properties.set("outputDir", outputDirProperty);
        
        // Modifiers
        ObjectNode modifiersProperty = objectMapper.createObjectNode();
        modifiersProperty.put("type", "array");
        modifiersProperty.put("description", "Access modifiers and other modifiers");
        ObjectNode modifierItems = objectMapper.createObjectNode();
        modifierItems.put("type", "string");
        ArrayNode modifierEnum = objectMapper.createArrayNode();
        modifierEnum.add("public").add("private").add("protected").add("static").add("final").add("abstract");
        modifierItems.set("enum", modifierEnum);
        modifiersProperty.set("items", modifierItems);
        properties.set("modifiers", modifiersProperty);
        
        // Superclass (for classes)
        ObjectNode superclassProperty = objectMapper.createObjectNode();
        superclassProperty.put("type", "string");
        superclassProperty.put("description", "Superclass name (for classes)");
        properties.set("superclass", superclassProperty);
        
        // Interfaces (for classes and interfaces)
        ObjectNode interfacesProperty = objectMapper.createObjectNode();
        interfacesProperty.put("type", "array");
        interfacesProperty.put("description", "Interface names to implement/extend");
        ObjectNode interfaceItems = objectMapper.createObjectNode();
        interfaceItems.put("type", "string");
        interfacesProperty.set("items", interfaceItems);
        properties.set("interfaces", interfacesProperty);
        
        // Fields (for classes and interfaces)
        ObjectNode fieldsProperty = objectMapper.createObjectNode();
        fieldsProperty.put("type", "array");
        fieldsProperty.put("description", "Fields to add to the class/interface");
        properties.set("fields", fieldsProperty);
        
        // Methods (for classes and interfaces)
        ObjectNode methodsProperty = objectMapper.createObjectNode();
        methodsProperty.put("type", "array");
        methodsProperty.put("description", "Methods to add to the class/interface");
        properties.set("methods", methodsProperty);
        
        // Annotations
        ObjectNode annotationsProperty = objectMapper.createObjectNode();
        annotationsProperty.put("type", "array");
        annotationsProperty.put("description", "Annotations to add");
        properties.set("annotations", annotationsProperty);
        
        return schema;
    }
    
    @Override
    protected McpToolResult doExecute(Map<String, Object> parameters) throws McpToolException {
        String type = getRequiredParameter(parameters, "type", String.class);
        String name = getRequiredParameter(parameters, "name", String.class);
        String packageName = getOptionalParameter(parameters, "packageName", "", String.class);
        String outputDir = getOptionalParameter(parameters, "outputDir", "src/main/java", String.class);
        
        validateNotEmpty(name, "name");
        
        try {
            switch (type.toLowerCase()) {
                case "class":
                    return generateClass(parameters, name, packageName, outputDir);
                case "interface":
                    return generateInterface(parameters, name, packageName, outputDir);
                case "enum":
                    return generateEnum(parameters, name, packageName, outputDir);
                case "method":
                    return generateMethod(parameters, name);
                case "field":
                    return generateField(parameters, name);
                default:
                    throw new McpToolException(getName(), "INVALID_TYPE", 
                            "Unsupported code generation type: " + type);
            }
        } catch (IOException e) {
            throw new McpToolException(getName(), "IO_ERROR", 
                    "Failed to write generated code to file", e);
        }
    }
    
    private McpToolResult generateClass(Map<String, Object> parameters, String name, 
                                       String packageName, String outputDir) throws IOException, McpToolException {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(name);
        
        // Add modifiers
        addModifiers(classBuilder, parameters);
        
        // Add superclass
        String superclass = getOptionalParameter(parameters, "superclass", null, String.class);
        if (superclass != null && !superclass.trim().isEmpty()) {
            classBuilder.superclass(ClassName.bestGuess(superclass));
        }
        
        // Add interfaces
        addInterfaces(classBuilder, parameters);
        
        // Add annotations
        addAnnotations(classBuilder, parameters);
        
        // Add fields
        addFields(classBuilder, parameters);
        
        // Add methods
        addMethods(classBuilder, parameters);
        
        TypeSpec classSpec = classBuilder.build();
        
        // Generate the Java file
        JavaFile.Builder javaFileBuilder = JavaFile.builder(packageName, classSpec);
        JavaFile javaFile = javaFileBuilder.build();
        
        // Write to file if output directory is specified
        String generatedCode = javaFile.toString();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedCode", generatedCode);
        metadata.put("className", name);
        metadata.put("packageName", packageName);
        
        if (!outputDir.trim().isEmpty()) {
            Path outputPath = Paths.get(outputDir);
            javaFile.writeTo(outputPath);
            metadata.put("outputPath", outputPath.resolve(packageName.replace('.', '/')).resolve(name + ".java").toString());
        }
        
        return McpToolResult.success("Java class generated successfully", metadata);
    }
    
    private McpToolResult generateInterface(Map<String, Object> parameters, String name, 
                                          String packageName, String outputDir) throws IOException, McpToolException {
        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(name);
        
        // Add modifiers
        addModifiers(interfaceBuilder, parameters);
        
        // Add extended interfaces
        addInterfaces(interfaceBuilder, parameters);
        
        // Add annotations
        addAnnotations(interfaceBuilder, parameters);
        
        // Add methods (interface methods are abstract by default)
        addMethods(interfaceBuilder, parameters);
        
        TypeSpec interfaceSpec = interfaceBuilder.build();
        
        // Generate the Java file
        JavaFile javaFile = JavaFile.builder(packageName, interfaceSpec).build();
        
        String generatedCode = javaFile.toString();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedCode", generatedCode);
        metadata.put("interfaceName", name);
        metadata.put("packageName", packageName);
        
        if (!outputDir.trim().isEmpty()) {
            Path outputPath = Paths.get(outputDir);
            javaFile.writeTo(outputPath);
            metadata.put("outputPath", outputPath.resolve(packageName.replace('.', '/')).resolve(name + ".java").toString());
        }
        
        return McpToolResult.success("Java interface generated successfully", metadata);
    }
    
    private McpToolResult generateEnum(Map<String, Object> parameters, String name, 
                                     String packageName, String outputDir) throws IOException, McpToolException {
        TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(name);
        
        // Add modifiers
        addModifiers(enumBuilder, parameters);
        
        // Add annotations
        addAnnotations(enumBuilder, parameters);
        
        // Add enum constants (if provided)
        @SuppressWarnings("unchecked")
        List<String> constants = getOptionalParameter(parameters, "constants", new ArrayList<>(), List.class);
        for (String constant : constants) {
            enumBuilder.addEnumConstant(constant);
        }
        
        TypeSpec enumSpec = enumBuilder.build();
        
        // Generate the Java file
        JavaFile javaFile = JavaFile.builder(packageName, enumSpec).build();
        
        String generatedCode = javaFile.toString();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedCode", generatedCode);
        metadata.put("enumName", name);
        metadata.put("packageName", packageName);
        
        if (!outputDir.trim().isEmpty()) {
            Path outputPath = Paths.get(outputDir);
            javaFile.writeTo(outputPath);
            metadata.put("outputPath", outputPath.resolve(packageName.replace('.', '/')).resolve(name + ".java").toString());
        }
        
        return McpToolResult.success("Java enum generated successfully", metadata);
    }
    
    private McpToolResult generateMethod(Map<String, Object> parameters, String name) throws McpToolException {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(name);
        
        // Add modifiers
        @SuppressWarnings("unchecked")
        List<String> modifiers = getOptionalParameter(parameters, "modifiers", new ArrayList<>(), List.class);
        for (String modifier : modifiers) {
            methodBuilder.addModifiers(parseModifier(modifier));
        }
        
        // Add return type
        String returnType = getOptionalParameter(parameters, "returnType", "void", String.class);
        if ("void".equals(returnType)) {
            methodBuilder.returns(TypeName.VOID);
        } else {
            methodBuilder.returns(ClassName.bestGuess(returnType));
        }
        
        // Add parameters
        @SuppressWarnings("unchecked")
        List<Map<String, String>> params = getOptionalParameter(parameters, "parameters", new ArrayList<>(), List.class);
        for (Map<String, String> param : params) {
            String paramType = param.get("type");
            String paramName = param.get("name");
            if (paramType != null && paramName != null) {
                methodBuilder.addParameter(ClassName.bestGuess(paramType), paramName);
            }
        }
        
        // Add method body
        String body = getOptionalParameter(parameters, "body", "", String.class);
        if (!body.trim().isEmpty()) {
            methodBuilder.addStatement(body);
        }
        
        MethodSpec methodSpec = methodBuilder.build();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedCode", methodSpec.toString());
        metadata.put("methodName", name);
        
        return McpToolResult.success("Java method generated successfully", metadata);
    }
    
    private McpToolResult generateField(Map<String, Object> parameters, String name) throws McpToolException {
        String fieldType = getRequiredParameter(parameters, "fieldType", String.class);
        
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(ClassName.bestGuess(fieldType), name);
        
        // Add modifiers
        @SuppressWarnings("unchecked")
        List<String> modifiers = getOptionalParameter(parameters, "modifiers", new ArrayList<>(), List.class);
        for (String modifier : modifiers) {
            fieldBuilder.addModifiers(parseModifier(modifier));
        }
        
        // Add initializer
        String initializer = getOptionalParameter(parameters, "initializer", null, String.class);
        if (initializer != null && !initializer.trim().isEmpty()) {
            fieldBuilder.initializer(initializer);
        }
        
        FieldSpec fieldSpec = fieldBuilder.build();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedCode", fieldSpec.toString());
        metadata.put("fieldName", name);
        metadata.put("fieldType", fieldType);
        
        return McpToolResult.success("Java field generated successfully", metadata);
    }
    
    private void addModifiers(TypeSpec.Builder builder, Map<String, Object> parameters) throws McpToolException {
        @SuppressWarnings("unchecked")
        List<String> modifiers = getOptionalParameter(parameters, "modifiers", new ArrayList<>(), List.class);
        for (String modifier : modifiers) {
            builder.addModifiers(parseModifier(modifier));
        }
    }
    
    private void addInterfaces(TypeSpec.Builder builder, Map<String, Object> parameters) throws McpToolException {
        @SuppressWarnings("unchecked")
        List<String> interfaces = getOptionalParameter(parameters, "interfaces", new ArrayList<>(), List.class);
        for (String interfaceName : interfaces) {
            if (!interfaceName.trim().isEmpty()) {
                builder.addSuperinterface(ClassName.bestGuess(interfaceName));
            }
        }
    }
    
    private void addAnnotations(TypeSpec.Builder builder, Map<String, Object> parameters) throws McpToolException {
        @SuppressWarnings("unchecked")
        List<String> annotations = getOptionalParameter(parameters, "annotations", new ArrayList<>(), List.class);
        for (String annotation : annotations) {
            if (!annotation.trim().isEmpty()) {
                builder.addAnnotation(ClassName.bestGuess(annotation));
            }
        }
    }
    
    private void addFields(TypeSpec.Builder builder, Map<String, Object> parameters) throws McpToolException {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = getOptionalParameter(parameters, "fields", new ArrayList<>(), List.class);
        for (Map<String, Object> field : fields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            if (fieldName != null && fieldType != null) {
                FieldSpec.Builder fieldBuilder = FieldSpec.builder(ClassName.bestGuess(fieldType), fieldName);
                
                @SuppressWarnings("unchecked")
                List<String> fieldModifiers = (List<String>) field.getOrDefault("modifiers", new ArrayList<>());
                for (String modifier : fieldModifiers) {
                    fieldBuilder.addModifiers(parseModifier(modifier));
                }
                
                String initializer = (String) field.get("initializer");
                if (initializer != null && !initializer.trim().isEmpty()) {
                    fieldBuilder.initializer(initializer);
                }
                
                builder.addField(fieldBuilder.build());
            }
        }
    }
    
    private void addMethods(TypeSpec.Builder builder, Map<String, Object> parameters) throws McpToolException {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = getOptionalParameter(parameters, "methods", new ArrayList<>(), List.class);
        for (Map<String, Object> method : methods) {
            String methodName = (String) method.get("name");
            if (methodName != null) {
                MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);
                
                @SuppressWarnings("unchecked")
                List<String> methodModifiers = (List<String>) method.getOrDefault("modifiers", new ArrayList<>());
                for (String modifier : methodModifiers) {
                    methodBuilder.addModifiers(parseModifier(modifier));
                }
                
                String returnType = (String) method.getOrDefault("returnType", "void");
                if ("void".equals(returnType)) {
                    methodBuilder.returns(TypeName.VOID);
                } else {
                    methodBuilder.returns(ClassName.bestGuess(returnType));
                }
                
                @SuppressWarnings("unchecked")
                List<Map<String, String>> params = (List<Map<String, String>>) method.getOrDefault("parameters", new ArrayList<>());
                for (Map<String, String> param : params) {
                    String paramType = param.get("type");
                    String paramName = param.get("name");
                    if (paramType != null && paramName != null) {
                        methodBuilder.addParameter(ClassName.bestGuess(paramType), paramName);
                    }
                }
                
                String body = (String) method.get("body");
                if (body != null && !body.trim().isEmpty()) {
                    methodBuilder.addStatement(body);
                }
                
                builder.addMethod(methodBuilder.build());
            }
        }
    }
    
    private Modifier parseModifier(String modifier) throws McpToolException {
        try {
            return switch (modifier.toLowerCase()) {
                case "public" -> Modifier.PUBLIC;
                case "private" -> Modifier.PRIVATE;
                case "protected" -> Modifier.PROTECTED;
                case "static" -> Modifier.STATIC;
                case "final" -> Modifier.FINAL;
                case "abstract" -> Modifier.ABSTRACT;
                case "synchronized" -> Modifier.SYNCHRONIZED;
                case "volatile" -> Modifier.VOLATILE;
                case "transient" -> Modifier.TRANSIENT;
                case "native" -> Modifier.NATIVE;
                case "strictfp" -> Modifier.STRICTFP;
                default -> throw new McpToolException(getName(), "INVALID_MODIFIER", 
                        "Unknown modifier: " + modifier);
            };
        } catch (Exception e) {
            throw new McpToolException(getName(), "INVALID_MODIFIER", 
                    "Failed to parse modifier: " + modifier, e);
        }
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        return switch (operation.toLowerCase()) {
            case "generate-class", "generate-interface", "generate-enum", 
                 "generate-method", "generate-field", "code-generation" -> true;
            default -> false;
        };
    }
}
