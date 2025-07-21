package com.phodal.remodern.tools.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.phodal.remodern.core.AbstractMcpTool;
import com.phodal.remodern.core.McpToolException;
import com.phodal.remodern.core.McpToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * MCP Tool for parsing Java source code using JavaParser
 */
public class JavaParseTool extends AbstractMcpTool {
    
    private final JavaParser javaParser;
    
    public JavaParseTool() {
        super("java-parse-tool", 
              "Parse and analyze Java source code. Extract classes, methods, fields, imports, and dependencies.",
              "parsing");
        
        this.javaParser = new JavaParser();
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = createBaseSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        ArrayNode required = (ArrayNode) schema.get("required");
        
        // Source file or directory
        ObjectNode sourceProperty = objectMapper.createObjectNode();
        sourceProperty.put("type", "string");
        sourceProperty.put("description", "Java source file path or directory to parse");
        properties.set("source", sourceProperty);
        required.add("source");
        
        // Analysis type
        ObjectNode analysisTypeProperty = objectMapper.createObjectNode();
        analysisTypeProperty.put("type", "string");
        analysisTypeProperty.put("description", "Type of analysis to perform");
        analysisTypeProperty.put("default", "full");
        ArrayNode analysisEnum = objectMapper.createArrayNode();
        analysisEnum.add("full").add("structure").add("methods").add("fields").add("imports").add("dependencies");
        analysisTypeProperty.set("enum", analysisEnum);
        properties.set("analysisType", analysisTypeProperty);
        
        // Include method bodies
        ObjectNode includeMethodBodiesProperty = objectMapper.createObjectNode();
        includeMethodBodiesProperty.put("type", "boolean");
        includeMethodBodiesProperty.put("description", "Whether to include method body analysis");
        includeMethodBodiesProperty.put("default", false);
        properties.set("includeMethodBodies", includeMethodBodiesProperty);
        
        // Include comments
        ObjectNode includeCommentsProperty = objectMapper.createObjectNode();
        includeCommentsProperty.put("type", "boolean");
        includeCommentsProperty.put("description", "Whether to include comments in analysis");
        includeCommentsProperty.put("default", true);
        properties.set("includeComments", includeCommentsProperty);
        
        // Include patterns
        ObjectNode includeProperty = objectMapper.createObjectNode();
        includeProperty.put("type", "array");
        includeProperty.put("description", "File patterns to include");
        ObjectNode includeItems = objectMapper.createObjectNode();
        includeItems.put("type", "string");
        includeProperty.set("items", includeItems);
        properties.set("include", includeProperty);
        
        // Exclude patterns
        ObjectNode excludeProperty = objectMapper.createObjectNode();
        excludeProperty.put("type", "array");
        excludeProperty.put("description", "File patterns to exclude");
        ObjectNode excludeItems = objectMapper.createObjectNode();
        excludeItems.put("type", "string");
        excludeProperty.set("items", excludeItems);
        properties.set("exclude", excludeProperty);
        
        return schema;
    }
    
    @Override
    protected McpToolResult doExecute(Map<String, Object> parameters) throws McpToolException {
        String source = getRequiredParameter(parameters, "source", String.class);
        String analysisType = getOptionalParameter(parameters, "analysisType", "full", String.class);
        boolean includeMethodBodies = getOptionalParameter(parameters, "includeMethodBodies", false, Boolean.class);
        boolean includeComments = getOptionalParameter(parameters, "includeComments", true, Boolean.class);
        
        validateNotEmpty(source, "source");
        
        try {
            Path sourcePath = Paths.get(source);
            if (!Files.exists(sourcePath)) {
                throw new McpToolException(getName(), "SOURCE_NOT_FOUND", 
                        "Source path not found: " + source);
            }
            
            List<Path> javaFiles = collectJavaFiles(sourcePath, parameters);
            if (javaFiles.isEmpty()) {
                throw new McpToolException(getName(), "NO_JAVA_FILES", 
                        "No Java files found in source: " + source);
            }
            
            Map<String, Object> analysisResult = new HashMap<>();
            List<Map<String, Object>> fileAnalyses = new ArrayList<>();
            
            for (Path javaFile : javaFiles) {
                Map<String, Object> fileAnalysis = analyzeJavaFile(javaFile, analysisType, 
                                                                  includeMethodBodies, includeComments);
                fileAnalyses.add(fileAnalysis);
            }
            
            analysisResult.put("files", fileAnalyses);
            analysisResult.put("totalFiles", javaFiles.size());
            analysisResult.put("analysisType", analysisType);
            
            // Generate summary
            Map<String, Object> summary = generateSummary(fileAnalyses);
            analysisResult.put("summary", summary);
            
            return McpToolResult.success("Java source analysis completed successfully", analysisResult);
            
        } catch (IOException e) {
            throw new McpToolException(getName(), "IO_ERROR", 
                    "Failed to read Java files", e);
        }
    }
    
    private List<Path> collectJavaFiles(Path sourcePath, Map<String, Object> parameters) throws IOException, McpToolException {
        @SuppressWarnings("unchecked")
        List<String> includePatterns = getOptionalParameter(parameters, "include", new ArrayList<>(), List.class);
        @SuppressWarnings("unchecked")
        List<String> excludePatterns = getOptionalParameter(parameters, "exclude", new ArrayList<>(), List.class);
        
        List<Path> javaFiles = new ArrayList<>();
        
        if (Files.isDirectory(sourcePath)) {
            Files.walk(sourcePath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> matchesPatterns(path, includePatterns, excludePatterns))
                    .forEach(javaFiles::add);
        } else if (sourcePath.toString().endsWith(".java")) {
            javaFiles.add(sourcePath);
        }
        
        return javaFiles;
    }
    
    private boolean matchesPatterns(Path path, List<String> includePatterns, List<String> excludePatterns) {
        String pathString = path.toString();
        
        // Check exclude patterns first
        for (String exclude : excludePatterns) {
            if (pathString.matches(exclude.replace("*", ".*"))) {
                return false;
            }
        }
        
        // If no include patterns, include by default
        if (includePatterns.isEmpty()) {
            return true;
        }
        
        // Check include patterns
        for (String include : includePatterns) {
            if (pathString.matches(include.replace("*", ".*"))) {
                return true;
            }
        }
        
        return false;
    }
    
    private Map<String, Object> analyzeJavaFile(Path javaFile, String analysisType, 
                                               boolean includeMethodBodies, boolean includeComments) 
            throws IOException, McpToolException {
        String content = Files.readString(javaFile);
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(content);
        if (!parseResult.isSuccessful()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("file", javaFile.toString());
            errorResult.put("parseErrors", parseResult.getProblems().toString());
            return errorResult;
        }
        
        CompilationUnit cu = parseResult.getResult().orElseThrow();
        Map<String, Object> analysis = new HashMap<>();
        
        analysis.put("file", javaFile.toString());
        analysis.put("size", content.length());
        analysis.put("lines", content.split("\n").length);
        
        switch (analysisType.toLowerCase()) {
            case "full":
                analysis.putAll(analyzeStructure(cu));
                analysis.putAll(analyzeMethods(cu, includeMethodBodies));
                analysis.putAll(analyzeFields(cu));
                analysis.putAll(analyzeImports(cu));
                analysis.putAll(analyzeDependencies(cu));
                if (includeComments) {
                    analysis.putAll(analyzeComments(cu));
                }
                break;
            case "structure":
                analysis.putAll(analyzeStructure(cu));
                break;
            case "methods":
                analysis.putAll(analyzeMethods(cu, includeMethodBodies));
                break;
            case "fields":
                analysis.putAll(analyzeFields(cu));
                break;
            case "imports":
                analysis.putAll(analyzeImports(cu));
                break;
            case "dependencies":
                analysis.putAll(analyzeDependencies(cu));
                break;
        }
        
        return analysis;
    }
    
    private Map<String, Object> analyzeStructure(CompilationUnit cu) {
        Map<String, Object> structure = new HashMap<>();
        
        // Package information
        cu.getPackageDeclaration().ifPresent(pkg -> 
            structure.put("package", pkg.getNameAsString()));
        
        // Types (classes, interfaces, enums)
        List<Map<String, Object>> types = new ArrayList<>();
        
        for (TypeDeclaration<?> type : cu.getTypes()) {
            Map<String, Object> typeInfo = new HashMap<>();
            typeInfo.put("name", type.getNameAsString());
            typeInfo.put("kind", getTypeKind(type));
            typeInfo.put("modifiers", type.getModifiers().stream()
                    .map(mod -> mod.getKeyword().asString()).toList());
            
            // Annotations
            List<String> annotations = type.getAnnotations().stream()
                    .map(AnnotationExpr::toString).toList();
            typeInfo.put("annotations", annotations);
            
            // Inheritance information
            if (type instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classOrInterface = (ClassOrInterfaceDeclaration) type;
                
                // Extended types
                List<String> extendedTypes = classOrInterface.getExtendedTypes().stream()
                        .map(ClassOrInterfaceType::getNameAsString).toList();
                typeInfo.put("extendedTypes", extendedTypes);
                
                // Implemented types
                List<String> implementedTypes = classOrInterface.getImplementedTypes().stream()
                        .map(ClassOrInterfaceType::getNameAsString).toList();
                typeInfo.put("implementedTypes", implementedTypes);
            }
            
            types.add(typeInfo);
        }
        
        structure.put("types", types);
        structure.put("typeCount", types.size());
        
        return structure;
    }
    
    private Map<String, Object> analyzeMethods(CompilationUnit cu, boolean includeMethodBodies) {
        Map<String, Object> methodsAnalysis = new HashMap<>();
        List<Map<String, Object>> methods = new ArrayList<>();
        
        MethodVisitor methodVisitor = new MethodVisitor(methods, includeMethodBodies);
        methodVisitor.visit(cu, null);
        
        methodsAnalysis.put("methods", methods);
        methodsAnalysis.put("methodCount", methods.size());
        
        return methodsAnalysis;
    }
    
    private Map<String, Object> analyzeFields(CompilationUnit cu) {
        Map<String, Object> fieldsAnalysis = new HashMap<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        
        FieldVisitor fieldVisitor = new FieldVisitor(fields);
        fieldVisitor.visit(cu, null);
        
        fieldsAnalysis.put("fields", fields);
        fieldsAnalysis.put("fieldCount", fields.size());
        
        return fieldsAnalysis;
    }
    
    private Map<String, Object> analyzeImports(CompilationUnit cu) {
        Map<String, Object> importsAnalysis = new HashMap<>();
        
        List<String> imports = cu.getImports().stream()
                .map(ImportDeclaration::getNameAsString).toList();
        
        List<String> staticImports = cu.getImports().stream()
                .filter(ImportDeclaration::isStatic)
                .map(ImportDeclaration::getNameAsString).toList();
        
        importsAnalysis.put("imports", imports);
        importsAnalysis.put("staticImports", staticImports);
        importsAnalysis.put("importCount", imports.size());
        importsAnalysis.put("staticImportCount", staticImports.size());
        
        return importsAnalysis;
    }
    
    private Map<String, Object> analyzeDependencies(CompilationUnit cu) {
        Map<String, Object> dependencies = new HashMap<>();
        Set<String> referencedTypes = new HashSet<>();
        
        // Add imports as dependencies
        cu.getImports().forEach(imp -> referencedTypes.add(imp.getNameAsString()));
        
        // Add extended and implemented types
        for (TypeDeclaration<?> type : cu.getTypes()) {
            if (type instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classOrInterface = (ClassOrInterfaceDeclaration) type;
                classOrInterface.getExtendedTypes().forEach(ext -> 
                    referencedTypes.add(ext.getNameAsString()));
                classOrInterface.getImplementedTypes().forEach(impl -> 
                    referencedTypes.add(impl.getNameAsString()));
            }
        }
        
        // Collect type references from method signatures and field types
        TypeReferenceVisitor typeVisitor = new TypeReferenceVisitor(referencedTypes);
        typeVisitor.visit(cu, null);
        
        List<String> sortedDependencies = referencedTypes.stream()
                .filter(dep -> !isPrimitiveType(dep))
                .sorted().toList();
        
        dependencies.put("referencedTypes", sortedDependencies);
        dependencies.put("dependencyCount", sortedDependencies.size());
        
        return dependencies;
    }
    
    private Map<String, Object> analyzeComments(CompilationUnit cu) {
        Map<String, Object> commentsAnalysis = new HashMap<>();
        
        List<Map<String, Object>> comments = new ArrayList<>();
        cu.getAllComments().forEach(comment -> {
            Map<String, Object> commentInfo = new HashMap<>();
            commentInfo.put("type", comment.getClass().getSimpleName());
            commentInfo.put("content", comment.getContent());
            comment.getRange().ifPresent(range -> {
                commentInfo.put("startLine", range.begin.line);
                commentInfo.put("endLine", range.end.line);
            });
            comments.add(commentInfo);
        });
        
        commentsAnalysis.put("comments", comments);
        commentsAnalysis.put("commentCount", comments.size());
        
        return commentsAnalysis;
    }
    
    private String getTypeKind(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classOrInterface = (ClassOrInterfaceDeclaration) type;
            return classOrInterface.isInterface() ? "interface" : "class";
        } else if (type instanceof EnumDeclaration) {
            return "enum";
        } else if (type instanceof AnnotationDeclaration) {
            return "annotation";
        } else if (type instanceof RecordDeclaration) {
            return "record";
        }
        return "unknown";
    }
    
    private boolean isPrimitiveType(String typeName) {
        return typeName.equals("boolean") || typeName.equals("byte") || typeName.equals("char") ||
               typeName.equals("short") || typeName.equals("int") || typeName.equals("long") ||
               typeName.equals("float") || typeName.equals("double") || typeName.equals("void");
    }
    
    private Map<String, Object> generateSummary(List<Map<String, Object>> fileAnalyses) {
        Map<String, Object> summary = new HashMap<>();
        
        int totalTypes = 0;
        int totalMethods = 0;
        int totalFields = 0;
        int totalImports = 0;
        Set<String> allDependencies = new HashSet<>();
        
        for (Map<String, Object> analysis : fileAnalyses) {
            if (analysis.containsKey("typeCount")) {
                totalTypes += (Integer) analysis.get("typeCount");
            }
            if (analysis.containsKey("methodCount")) {
                totalMethods += (Integer) analysis.get("methodCount");
            }
            if (analysis.containsKey("fieldCount")) {
                totalFields += (Integer) analysis.get("fieldCount");
            }
            if (analysis.containsKey("importCount")) {
                totalImports += (Integer) analysis.get("importCount");
            }
            if (analysis.containsKey("referencedTypes")) {
                @SuppressWarnings("unchecked")
                List<String> deps = (List<String>) analysis.get("referencedTypes");
                allDependencies.addAll(deps);
            }
        }
        
        summary.put("totalTypes", totalTypes);
        summary.put("totalMethods", totalMethods);
        summary.put("totalFields", totalFields);
        summary.put("totalImports", totalImports);
        summary.put("uniqueDependencies", allDependencies.size());
        
        return summary;
    }
    
    // Visitor classes for collecting specific information
    private static class MethodVisitor extends VoidVisitorAdapter<Void> {
        private final List<Map<String, Object>> methods;
        private final boolean includeMethodBodies;
        
        public MethodVisitor(List<Map<String, Object>> methods, boolean includeMethodBodies) {
            this.methods = methods;
            this.includeMethodBodies = includeMethodBodies;
        }
        
        @Override
        public void visit(MethodDeclaration method, Void arg) {
            Map<String, Object> methodInfo = new HashMap<>();
            methodInfo.put("name", method.getNameAsString());
            methodInfo.put("returnType", method.getType().asString());
            methodInfo.put("modifiers", method.getModifiers().stream()
                    .map(mod -> mod.getKeyword().asString()).toList());
            
            // Parameters
            List<Map<String, Object>> parameters = new ArrayList<>();
            method.getParameters().forEach(param -> {
                Map<String, Object> paramInfo = new HashMap<>();
                paramInfo.put("name", param.getNameAsString());
                paramInfo.put("type", param.getType().asString());
                parameters.add(paramInfo);
            });
            methodInfo.put("parameters", parameters);
            
            // Annotations
            List<String> annotations = method.getAnnotations().stream()
                    .map(AnnotationExpr::toString).toList();
            methodInfo.put("annotations", annotations);
            
            // Thrown exceptions
            List<String> thrownExceptions = method.getThrownExceptions().stream()
                    .map(Object::toString).toList();
            methodInfo.put("thrownExceptions", thrownExceptions);
            
            if (includeMethodBodies && method.getBody().isPresent()) {
                methodInfo.put("body", method.getBody().get().toString());
            }
            
            methods.add(methodInfo);
            super.visit(method, arg);
        }
    }
    
    private static class FieldVisitor extends VoidVisitorAdapter<Void> {
        private final List<Map<String, Object>> fields;
        
        public FieldVisitor(List<Map<String, Object>> fields) {
            this.fields = fields;
        }
        
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            field.getVariables().forEach(variable -> {
                Map<String, Object> fieldInfo = new HashMap<>();
                fieldInfo.put("name", variable.getNameAsString());
                fieldInfo.put("type", field.getElementType().asString());
                fieldInfo.put("modifiers", field.getModifiers().stream()
                        .map(mod -> mod.getKeyword().asString()).toList());
                
                // Annotations
                List<String> annotations = field.getAnnotations().stream()
                        .map(AnnotationExpr::toString).toList();
                fieldInfo.put("annotations", annotations);
                
                // Initial value
                variable.getInitializer().ifPresent(init -> 
                    fieldInfo.put("initializer", init.toString()));
                
                fields.add(fieldInfo);
            });
            super.visit(field, arg);
        }
    }
    
    private static class TypeReferenceVisitor extends VoidVisitorAdapter<Void> {
        private final Set<String> referencedTypes;
        
        public TypeReferenceVisitor(Set<String> referencedTypes) {
            this.referencedTypes = referencedTypes;
        }
        
        @Override
        public void visit(ClassOrInterfaceType type, Void arg) {
            referencedTypes.add(type.getNameAsString());
            super.visit(type, arg);
        }
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        return switch (operation.toLowerCase()) {
            case "java-parse", "source-analysis", "ast-analysis", "java-structure" -> true;
            default -> false;
        };
    }
}
