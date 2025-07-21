package com.phodal.remodern.tools.bytecode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phodal.remodern.core.AbstractMcpTool;
import com.phodal.remodern.core.McpToolException;
import com.phodal.remodern.core.McpToolResult;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * MCP Tool for analyzing Java bytecode using ASM
 */
public class ByteCodeTool extends AbstractMcpTool {
    
    public ByteCodeTool() {
        super("bytecode-tool", 
              "Analyze Java bytecode using ASM. Supports class files, JAR files, and bytecode inspection.",
              "bytecode-analysis");
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = createBaseSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        ArrayNode required = (ArrayNode) schema.get("required");
        
        // Source file or directory
        ObjectNode sourceProperty = objectMapper.createObjectNode();
        sourceProperty.put("type", "string");
        sourceProperty.put("description", "Path to .class file, .jar file, or directory containing bytecode");
        properties.set("source", sourceProperty);
        required.add("source");
        
        // Analysis type
        ObjectNode analysisTypeProperty = objectMapper.createObjectNode();
        analysisTypeProperty.put("type", "string");
        analysisTypeProperty.put("description", "Type of bytecode analysis to perform");
        analysisTypeProperty.put("default", "full");
        ArrayNode analysisEnum = objectMapper.createArrayNode();
        analysisEnum.add("full").add("structure").add("methods").add("fields").add("dependencies").add("instructions");
        analysisTypeProperty.set("enum", analysisEnum);
        properties.set("analysisType", analysisTypeProperty);
        
        // Class name filter (for JAR files)
        ObjectNode classFilterProperty = objectMapper.createObjectNode();
        classFilterProperty.put("type", "string");
        classFilterProperty.put("description", "Class name pattern to filter (regex supported)");
        properties.set("classFilter", classFilterProperty);
        
        // Include method bytecode
        ObjectNode includeMethodBytecodeProperty = objectMapper.createObjectNode();
        includeMethodBytecodeProperty.put("type", "boolean");
        includeMethodBytecodeProperty.put("description", "Whether to include detailed method bytecode instructions");
        includeMethodBytecodeProperty.put("default", false);
        properties.set("includeMethodBytecode", includeMethodBytecodeProperty);
        
        // Include debug info
        ObjectNode includeDebugProperty = objectMapper.createObjectNode();
        includeDebugProperty.put("type", "boolean");
        includeDebugProperty.put("description", "Whether to include debug information (line numbers, local variables)");
        includeDebugProperty.put("default", true);
        properties.set("includeDebug", includeDebugProperty);
        
        // Analyze dependencies
        ObjectNode analyzeDepsProperty = objectMapper.createObjectNode();
        analyzeDepsProperty.put("type", "boolean");
        analyzeDepsProperty.put("description", "Whether to analyze class dependencies");
        analyzeDepsProperty.put("default", true);
        properties.set("analyzeDependencies", analyzeDepsProperty);
        
        return schema;
    }
    
    @Override
    protected McpToolResult doExecute(Map<String, Object> parameters) throws McpToolException {
        String source = getRequiredParameter(parameters, "source", String.class);
        String analysisType = getOptionalParameter(parameters, "analysisType", "full", String.class);
        String classFilter = getOptionalParameter(parameters, "classFilter", null, String.class);
        boolean includeMethodBytecode = getOptionalParameter(parameters, "includeMethodBytecode", false, Boolean.class);
        boolean includeDebug = getOptionalParameter(parameters, "includeDebug", true, Boolean.class);
        boolean analyzeDependencies = getOptionalParameter(parameters, "analyzeDependencies", true, Boolean.class);
        
        validateNotEmpty(source, "source");
        
        try {
            Path sourcePath = Paths.get(source);
            if (!Files.exists(sourcePath)) {
                throw new McpToolException(getName(), "SOURCE_NOT_FOUND", 
                        "Source path not found: " + source);
            }
            
            Map<String, Object> analysisResult = new HashMap<>();
            
            if (source.endsWith(".jar")) {
                analysisResult = analyzeJarFile(sourcePath, analysisType, classFilter, 
                                              includeMethodBytecode, includeDebug, analyzeDependencies);
            } else if (source.endsWith(".class")) {
                analysisResult = analyzeClassFile(sourcePath, analysisType, 
                                                includeMethodBytecode, includeDebug, analyzeDependencies);
            } else if (Files.isDirectory(sourcePath)) {
                analysisResult = analyzeDirectory(sourcePath, analysisType, classFilter, 
                                                includeMethodBytecode, includeDebug, analyzeDependencies);
            } else {
                throw new McpToolException(getName(), "INVALID_SOURCE", 
                        "Source must be a .class file, .jar file, or directory");
            }
            
            return McpToolResult.success("Bytecode analysis completed successfully", analysisResult);
            
        } catch (IOException e) {
            throw new McpToolException(getName(), "IO_ERROR", 
                    "Failed to read bytecode files", e);
        }
    }
    
    private Map<String, Object> analyzeJarFile(Path jarPath, String analysisType, String classFilter,
                                              boolean includeMethodBytecode, boolean includeDebug, 
                                              boolean analyzeDependencies) throws IOException, McpToolException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> classes = new ArrayList<>();
        
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");
                    
                    // Apply class filter if specified
                    if (classFilter != null && !className.matches(classFilter)) {
                        continue;
                    }
                    
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        Map<String, Object> classAnalysis = analyzeClassBytes(inputStream, analysisType, 
                                                                             includeMethodBytecode, includeDebug, analyzeDependencies);
                        classAnalysis.put("jarEntry", entry.getName());
                        classes.add(classAnalysis);
                    }
                }
            }
        }
        
        result.put("jarFile", jarPath.toString());
        result.put("classes", classes);
        result.put("totalClasses", classes.size());
        
        if (analyzeDependencies) {
            result.put("dependencies", extractJarDependencies(classes));
        }
        
        return result;
    }
    
    private Map<String, Object> analyzeClassFile(Path classPath, String analysisType,
                                                 boolean includeMethodBytecode, boolean includeDebug, 
                                                 boolean analyzeDependencies) throws IOException, McpToolException {
        try (InputStream inputStream = Files.newInputStream(classPath)) {
            Map<String, Object> result = analyzeClassBytes(inputStream, analysisType, 
                                                          includeMethodBytecode, includeDebug, analyzeDependencies);
            result.put("classFile", classPath.toString());
            return result;
        }
    }
    
    private Map<String, Object> analyzeDirectory(Path dirPath, String analysisType, String classFilter,
                                                 boolean includeMethodBytecode, boolean includeDebug, 
                                                 boolean analyzeDependencies) throws IOException, McpToolException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> classes = new ArrayList<>();
        
        Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".class"))
                .forEach(classFile -> {
                    try {
                        String className = dirPath.relativize(classFile).toString()
                                .replace('/', '.').replace('\\', '.').replace(".class", "");
                        
                        // Apply class filter if specified
                        if (classFilter != null && !className.matches(classFilter)) {
                            return;
                        }
                        
                        Map<String, Object> classAnalysis = analyzeClassFile(classFile, analysisType, 
                                                                            includeMethodBytecode, includeDebug, analyzeDependencies);
                        classes.add(classAnalysis);
                    } catch (Exception e) {
                        logger.warn("Failed to analyze class file: " + classFile, e);
                    }
                });
        
        result.put("directory", dirPath.toString());
        result.put("classes", classes);
        result.put("totalClasses", classes.size());
        
        if (analyzeDependencies) {
            result.put("dependencies", extractJarDependencies(classes));
        }
        
        return result;
    }
    
    private Map<String, Object> analyzeClassBytes(InputStream inputStream, String analysisType,
                                                  boolean includeMethodBytecode, boolean includeDebug, 
                                                  boolean analyzeDependencies) throws IOException, McpToolException {
        ClassReader classReader = new ClassReader(inputStream);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, includeDebug ? 0 : ClassReader.SKIP_DEBUG);
        
        Map<String, Object> analysis = new HashMap<>();
        
        switch (analysisType.toLowerCase()) {
            case "full":
                analysis.putAll(analyzeClassStructure(classNode));
                analysis.putAll(analyzeClassMethods(classNode, includeMethodBytecode));
                analysis.putAll(analyzeClassFields(classNode));
                if (analyzeDependencies) {
                    analysis.putAll(analyzeClassDependencies(classNode));
                }
                break;
            case "structure":
                analysis.putAll(analyzeClassStructure(classNode));
                break;
            case "methods":
                analysis.putAll(analyzeClassMethods(classNode, includeMethodBytecode));
                break;
            case "fields":
                analysis.putAll(analyzeClassFields(classNode));
                break;
            case "dependencies":
                analysis.putAll(analyzeClassDependencies(classNode));
                break;
            case "instructions":
                analysis.putAll(analyzeClassInstructions(classNode));
                break;
        }
        
        return analysis;
    }
    
    private Map<String, Object> analyzeClassStructure(ClassNode classNode) {
        Map<String, Object> structure = new HashMap<>();
        
        structure.put("className", classNode.name.replace('/', '.'));
        structure.put("access", getAccessFlags(classNode.access));
        structure.put("version", classNode.version);
        structure.put("superClass", classNode.superName != null ? classNode.superName.replace('/', '.') : null);
        
        List<String> interfaces = new ArrayList<>();
        if (classNode.interfaces != null) {
            for (String iface : classNode.interfaces) {
                interfaces.add(iface.replace('/', '.'));
            }
        }
        structure.put("interfaces", interfaces);
        
        structure.put("signature", classNode.signature);
        structure.put("sourceFile", classNode.sourceFile);
        
        // Annotations
        List<Map<String, Object>> annotations = new ArrayList<>();
        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.visibleAnnotations) {
                annotations.add(analyzeAnnotation(annotation));
            }
        }
        structure.put("annotations", annotations);
        
        return structure;
    }
    
    private Map<String, Object> analyzeClassMethods(ClassNode classNode, boolean includeMethodBytecode) {
        Map<String, Object> methodsAnalysis = new HashMap<>();
        List<Map<String, Object>> methods = new ArrayList<>();
        
        for (MethodNode method : classNode.methods) {
            Map<String, Object> methodInfo = new HashMap<>();
            methodInfo.put("name", method.name);
            methodInfo.put("descriptor", method.desc);
            methodInfo.put("access", getAccessFlags(method.access));
            methodInfo.put("signature", method.signature);
            
            // Exceptions
            if (method.exceptions != null && !method.exceptions.isEmpty()) {
                List<String> exceptions = new ArrayList<>();
                for (String exception : method.exceptions) {
                    exceptions.add(exception.replace('/', '.'));
                }
                methodInfo.put("exceptions", exceptions);
            }
            
            // Annotations
            List<Map<String, Object>> annotations = new ArrayList<>();
            if (method.visibleAnnotations != null) {
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    annotations.add(analyzeAnnotation(annotation));
                }
            }
            methodInfo.put("annotations", annotations);
            
            // Method bytecode instructions
            if (includeMethodBytecode && method.instructions != null) {
                methodInfo.put("instructions", analyzeMethodInstructions(method));
            }
            
            // Local variables
            if (method.localVariables != null) {
                List<Map<String, Object>> localVars = new ArrayList<>();
                for (LocalVariableNode localVar : method.localVariables) {
                    Map<String, Object> varInfo = new HashMap<>();
                    varInfo.put("name", localVar.name);
                    varInfo.put("descriptor", localVar.desc);
                    varInfo.put("signature", localVar.signature);
                    varInfo.put("index", localVar.index);
                    localVars.add(varInfo);
                }
                methodInfo.put("localVariables", localVars);
            }
            
            methods.add(methodInfo);
        }
        
        methodsAnalysis.put("methods", methods);
        methodsAnalysis.put("methodCount", methods.size());
        
        return methodsAnalysis;
    }
    
    private Map<String, Object> analyzeClassFields(ClassNode classNode) {
        Map<String, Object> fieldsAnalysis = new HashMap<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        
        for (FieldNode field : classNode.fields) {
            Map<String, Object> fieldInfo = new HashMap<>();
            fieldInfo.put("name", field.name);
            fieldInfo.put("descriptor", field.desc);
            fieldInfo.put("access", getAccessFlags(field.access));
            fieldInfo.put("signature", field.signature);
            fieldInfo.put("value", field.value);
            
            // Annotations
            List<Map<String, Object>> annotations = new ArrayList<>();
            if (field.visibleAnnotations != null) {
                for (AnnotationNode annotation : field.visibleAnnotations) {
                    annotations.add(analyzeAnnotation(annotation));
                }
            }
            fieldInfo.put("annotations", annotations);
            
            fields.add(fieldInfo);
        }
        
        fieldsAnalysis.put("fields", fields);
        fieldsAnalysis.put("fieldCount", fields.size());
        
        return fieldsAnalysis;
    }
    
    private Map<String, Object> analyzeClassDependencies(ClassNode classNode) {
        Map<String, Object> dependencies = new HashMap<>();
        Set<String> referencedClasses = new HashSet<>();
        
        // Add superclass and interfaces
        if (classNode.superName != null) {
            referencedClasses.add(classNode.superName.replace('/', '.'));
        }
        if (classNode.interfaces != null) {
            for (String iface : classNode.interfaces) {
                referencedClasses.add(iface.replace('/', '.'));
            }
        }
        
        // Analyze method dependencies
        for (MethodNode method : classNode.methods) {
            if (method.instructions != null) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode) {
                        TypeInsnNode typeInsn = (TypeInsnNode) instruction;
                        referencedClasses.add(typeInsn.desc.replace('/', '.'));
                    } else if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode fieldInsn = (FieldInsnNode) instruction;
                        referencedClasses.add(fieldInsn.owner.replace('/', '.'));
                    } else if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode methodInsn = (MethodInsnNode) instruction;
                        referencedClasses.add(methodInsn.owner.replace('/', '.'));
                    }
                }
            }
        }
        
        // Filter out primitive types and array markers
        List<String> filteredDependencies = referencedClasses.stream()
                .filter(className -> !className.startsWith("[") && !isPrimitiveType(className))
                .sorted()
                .toList();
        
        dependencies.put("referencedClasses", filteredDependencies);
        dependencies.put("dependencyCount", filteredDependencies.size());
        
        return dependencies;
    }
    
    private Map<String, Object> analyzeClassInstructions(ClassNode classNode) {
        Map<String, Object> instructionsAnalysis = new HashMap<>();
        Map<String, Integer> instructionCounts = new HashMap<>();
        int totalInstructions = 0;
        
        for (MethodNode method : classNode.methods) {
            if (method.instructions != null) {
                for (AbstractInsnNode instruction : method.instructions) {
                    String opcodeName = getOpcodeName(instruction.getOpcode());
                    instructionCounts.merge(opcodeName, 1, Integer::sum);
                    totalInstructions++;
                }
            }
        }
        
        instructionsAnalysis.put("instructionCounts", instructionCounts);
        instructionsAnalysis.put("totalInstructions", totalInstructions);
        instructionsAnalysis.put("uniqueInstructions", instructionCounts.size());
        
        return instructionsAnalysis;
    }
    
    private List<Map<String, Object>> analyzeMethodInstructions(MethodNode method) {
        List<Map<String, Object>> instructions = new ArrayList<>();
        
        if (method.instructions != null) {
            for (AbstractInsnNode instruction : method.instructions) {
                Map<String, Object> instrInfo = new HashMap<>();
                instrInfo.put("opcode", instruction.getOpcode());
                instrInfo.put("opcodeName", getOpcodeName(instruction.getOpcode()));
                instrInfo.put("type", instruction.getType());
                
                // Add instruction-specific details
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsn = (FieldInsnNode) instruction;
                    instrInfo.put("owner", fieldInsn.owner);
                    instrInfo.put("name", fieldInsn.name);
                    instrInfo.put("descriptor", fieldInsn.desc);
                } else if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode methodInsn = (MethodInsnNode) instruction;
                    instrInfo.put("owner", methodInsn.owner);
                    instrInfo.put("name", methodInsn.name);
                    instrInfo.put("descriptor", methodInsn.desc);
                } else if (instruction instanceof TypeInsnNode) {
                    TypeInsnNode typeInsn = (TypeInsnNode) instruction;
                    instrInfo.put("desc", typeInsn.desc);
                } else if (instruction instanceof LdcInsnNode) {
                    LdcInsnNode ldcInsn = (LdcInsnNode) instruction;
                    instrInfo.put("constant", ldcInsn.cst);
                }
                
                instructions.add(instrInfo);
            }
        }
        
        return instructions;
    }
    
    private Map<String, Object> analyzeAnnotation(AnnotationNode annotation) {
        Map<String, Object> annotationInfo = new HashMap<>();
        annotationInfo.put("descriptor", annotation.desc);
        
        if (annotation.values != null) {
            Map<String, Object> values = new HashMap<>();
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String key = (String) annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                values.put(key, value);
            }
            annotationInfo.put("values", values);
        }
        
        return annotationInfo;
    }
    
    private List<String> getAccessFlags(int access) {
        List<String> flags = new ArrayList<>();
        
        if ((access & Opcodes.ACC_PUBLIC) != 0) flags.add("public");
        if ((access & Opcodes.ACC_PRIVATE) != 0) flags.add("private");
        if ((access & Opcodes.ACC_PROTECTED) != 0) flags.add("protected");
        if ((access & Opcodes.ACC_STATIC) != 0) flags.add("static");
        if ((access & Opcodes.ACC_FINAL) != 0) flags.add("final");
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) flags.add("synchronized");
        if ((access & Opcodes.ACC_VOLATILE) != 0) flags.add("volatile");
        if ((access & Opcodes.ACC_TRANSIENT) != 0) flags.add("transient");
        if ((access & Opcodes.ACC_NATIVE) != 0) flags.add("native");
        if ((access & Opcodes.ACC_INTERFACE) != 0) flags.add("interface");
        if ((access & Opcodes.ACC_ABSTRACT) != 0) flags.add("abstract");
        if ((access & Opcodes.ACC_STRICT) != 0) flags.add("strictfp");
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) flags.add("synthetic");
        if ((access & Opcodes.ACC_ANNOTATION) != 0) flags.add("annotation");
        if ((access & Opcodes.ACC_ENUM) != 0) flags.add("enum");
        
        return flags;
    }
    
    private String getOpcodeName(int opcode) {
        if (opcode == -1) return "LABEL";
        
        try {
            return switch (opcode) {
                case Opcodes.NOP -> "NOP";
                case Opcodes.ACONST_NULL -> "ACONST_NULL";
                case Opcodes.ICONST_M1 -> "ICONST_M1";
                case Opcodes.ICONST_0 -> "ICONST_0";
                case Opcodes.ICONST_1 -> "ICONST_1";
                case Opcodes.ICONST_2 -> "ICONST_2";
                case Opcodes.ICONST_3 -> "ICONST_3";
                case Opcodes.ICONST_4 -> "ICONST_4";
                case Opcodes.ICONST_5 -> "ICONST_5";
                case Opcodes.LCONST_0 -> "LCONST_0";
                case Opcodes.LCONST_1 -> "LCONST_1";
                case Opcodes.FCONST_0 -> "FCONST_0";
                case Opcodes.FCONST_1 -> "FCONST_1";
                case Opcodes.FCONST_2 -> "FCONST_2";
                case Opcodes.DCONST_0 -> "DCONST_0";
                case Opcodes.DCONST_1 -> "DCONST_1";
                case Opcodes.BIPUSH -> "BIPUSH";
                case Opcodes.SIPUSH -> "SIPUSH";
                case Opcodes.LDC -> "LDC";
                case Opcodes.ILOAD -> "ILOAD";
                case Opcodes.LLOAD -> "LLOAD";
                case Opcodes.FLOAD -> "FLOAD";
                case Opcodes.DLOAD -> "DLOAD";
                case Opcodes.ALOAD -> "ALOAD";
                case Opcodes.IALOAD -> "IALOAD";
                case Opcodes.LALOAD -> "LALOAD";
                case Opcodes.FALOAD -> "FALOAD";
                case Opcodes.DALOAD -> "DALOAD";
                case Opcodes.AALOAD -> "AALOAD";
                case Opcodes.BALOAD -> "BALOAD";
                case Opcodes.CALOAD -> "CALOAD";
                case Opcodes.SALOAD -> "SALOAD";
                case Opcodes.ISTORE -> "ISTORE";
                case Opcodes.LSTORE -> "LSTORE";
                case Opcodes.FSTORE -> "FSTORE";
                case Opcodes.DSTORE -> "DSTORE";
                case Opcodes.ASTORE -> "ASTORE";
                case Opcodes.IASTORE -> "IASTORE";
                case Opcodes.LASTORE -> "LASTORE";
                case Opcodes.FASTORE -> "FASTORE";
                case Opcodes.DASTORE -> "DASTORE";
                case Opcodes.AASTORE -> "AASTORE";
                case Opcodes.BASTORE -> "BASTORE";
                case Opcodes.CASTORE -> "CASTORE";
                case Opcodes.SASTORE -> "SASTORE";
                case Opcodes.POP -> "POP";
                case Opcodes.POP2 -> "POP2";
                case Opcodes.DUP -> "DUP";
                case Opcodes.DUP_X1 -> "DUP_X1";
                case Opcodes.DUP_X2 -> "DUP_X2";
                case Opcodes.DUP2 -> "DUP2";
                case Opcodes.DUP2_X1 -> "DUP2_X1";
                case Opcodes.DUP2_X2 -> "DUP2_X2";
                case Opcodes.SWAP -> "SWAP";
                case Opcodes.IADD -> "IADD";
                case Opcodes.LADD -> "LADD";
                case Opcodes.FADD -> "FADD";
                case Opcodes.DADD -> "DADD";
                case Opcodes.ISUB -> "ISUB";
                case Opcodes.LSUB -> "LSUB";
                case Opcodes.FSUB -> "FSUB";
                case Opcodes.DSUB -> "DSUB";
                case Opcodes.IMUL -> "IMUL";
                case Opcodes.LMUL -> "LMUL";
                case Opcodes.FMUL -> "FMUL";
                case Opcodes.DMUL -> "DMUL";
                case Opcodes.IDIV -> "IDIV";
                case Opcodes.LDIV -> "LDIV";
                case Opcodes.FDIV -> "FDIV";
                case Opcodes.DDIV -> "DDIV";
                case Opcodes.IREM -> "IREM";
                case Opcodes.LREM -> "LREM";
                case Opcodes.FREM -> "FREM";
                case Opcodes.DREM -> "DREM";
                case Opcodes.INEG -> "INEG";
                case Opcodes.LNEG -> "LNEG";
                case Opcodes.FNEG -> "FNEG";
                case Opcodes.DNEG -> "DNEG";
                case Opcodes.ISHL -> "ISHL";
                case Opcodes.LSHL -> "LSHL";
                case Opcodes.ISHR -> "ISHR";
                case Opcodes.LSHR -> "LSHR";
                case Opcodes.IUSHR -> "IUSHR";
                case Opcodes.LUSHR -> "LUSHR";
                case Opcodes.IAND -> "IAND";
                case Opcodes.LAND -> "LAND";
                case Opcodes.IOR -> "IOR";
                case Opcodes.LOR -> "LOR";
                case Opcodes.IXOR -> "IXOR";
                case Opcodes.LXOR -> "LXOR";
                case Opcodes.IINC -> "IINC";
                case Opcodes.I2L -> "I2L";
                case Opcodes.I2F -> "I2F";
                case Opcodes.I2D -> "I2D";
                case Opcodes.L2I -> "L2I";
                case Opcodes.L2F -> "L2F";
                case Opcodes.L2D -> "L2D";
                case Opcodes.F2I -> "F2I";
                case Opcodes.F2L -> "F2L";
                case Opcodes.F2D -> "F2D";
                case Opcodes.D2I -> "D2I";
                case Opcodes.D2L -> "D2L";
                case Opcodes.D2F -> "D2F";
                case Opcodes.I2B -> "I2B";
                case Opcodes.I2C -> "I2C";
                case Opcodes.I2S -> "I2S";
                case Opcodes.LCMP -> "LCMP";
                case Opcodes.FCMPL -> "FCMPL";
                case Opcodes.FCMPG -> "FCMPG";
                case Opcodes.DCMPL -> "DCMPL";
                case Opcodes.DCMPG -> "DCMPG";
                case Opcodes.IFEQ -> "IFEQ";
                case Opcodes.IFNE -> "IFNE";
                case Opcodes.IFLT -> "IFLT";
                case Opcodes.IFGE -> "IFGE";
                case Opcodes.IFGT -> "IFGT";
                case Opcodes.IFLE -> "IFLE";
                case Opcodes.IF_ICMPEQ -> "IF_ICMPEQ";
                case Opcodes.IF_ICMPNE -> "IF_ICMPNE";
                case Opcodes.IF_ICMPLT -> "IF_ICMPLT";
                case Opcodes.IF_ICMPGE -> "IF_ICMPGE";
                case Opcodes.IF_ICMPGT -> "IF_ICMPGT";
                case Opcodes.IF_ICMPLE -> "IF_ICMPLE";
                case Opcodes.IF_ACMPEQ -> "IF_ACMPEQ";
                case Opcodes.IF_ACMPNE -> "IF_ACMPNE";
                case Opcodes.GOTO -> "GOTO";
                case Opcodes.JSR -> "JSR";
                case Opcodes.RET -> "RET";
                case Opcodes.TABLESWITCH -> "TABLESWITCH";
                case Opcodes.LOOKUPSWITCH -> "LOOKUPSWITCH";
                case Opcodes.IRETURN -> "IRETURN";
                case Opcodes.LRETURN -> "LRETURN";
                case Opcodes.FRETURN -> "FRETURN";
                case Opcodes.DRETURN -> "DRETURN";
                case Opcodes.ARETURN -> "ARETURN";
                case Opcodes.RETURN -> "RETURN";
                case Opcodes.GETSTATIC -> "GETSTATIC";
                case Opcodes.PUTSTATIC -> "PUTSTATIC";
                case Opcodes.GETFIELD -> "GETFIELD";
                case Opcodes.PUTFIELD -> "PUTFIELD";
                case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
                case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
                case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
                case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
                case Opcodes.INVOKEDYNAMIC -> "INVOKEDYNAMIC";
                case Opcodes.NEW -> "NEW";
                case Opcodes.NEWARRAY -> "NEWARRAY";
                case Opcodes.ANEWARRAY -> "ANEWARRAY";
                case Opcodes.ARRAYLENGTH -> "ARRAYLENGTH";
                case Opcodes.ATHROW -> "ATHROW";
                case Opcodes.CHECKCAST -> "CHECKCAST";
                case Opcodes.INSTANCEOF -> "INSTANCEOF";
                case Opcodes.MONITORENTER -> "MONITORENTER";
                case Opcodes.MONITOREXIT -> "MONITOREXIT";
                case Opcodes.MULTIANEWARRAY -> "MULTIANEWARRAY";
                case Opcodes.IFNULL -> "IFNULL";
                case Opcodes.IFNONNULL -> "IFNONNULL";
                default -> "UNKNOWN_" + opcode;
            };
        } catch (Exception e) {
            return "UNKNOWN_" + opcode;
        }
    }
    
    private boolean isPrimitiveType(String className) {
        return className.equals("boolean") || className.equals("byte") || className.equals("char") ||
               className.equals("short") || className.equals("int") || className.equals("long") ||
               className.equals("float") || className.equals("double") || className.equals("void");
    }
    
    private Map<String, Object> extractJarDependencies(List<Map<String, Object>> classes) {
        Set<String> allDependencies = new HashSet<>();
        
        for (Map<String, Object> classAnalysis : classes) {
            @SuppressWarnings("unchecked")
            List<String> classDeps = (List<String>) classAnalysis.get("referencedClasses");
            if (classDeps != null) {
                allDependencies.addAll(classDeps);
            }
        }
        
        Map<String, Object> dependencies = new HashMap<>();
        dependencies.put("allReferencedClasses", new ArrayList<>(allDependencies));
        dependencies.put("totalUniqueDependencies", allDependencies.size());
        
        return dependencies;
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        return switch (operation.toLowerCase()) {
            case "bytecode-analysis", "class-analysis", "jar-analysis", "asm-analysis" -> true;
            default -> false;
        };
    }
}
