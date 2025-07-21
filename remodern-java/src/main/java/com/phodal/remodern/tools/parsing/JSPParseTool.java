package com.phodal.remodern.tools.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phodal.remodern.core.AbstractMcpTool;
import com.phodal.remodern.core.McpToolException;
import com.phodal.remodern.core.McpToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP Tool for parsing and analyzing JSP files
 */
public class JSPParseTool extends AbstractMcpTool {
    
    // JSP element patterns
    private static final Pattern JSP_DIRECTIVE_PATTERN = Pattern.compile("<%@\\s*(\\w+)\\s+([^%>]*)%>");
    private static final Pattern JSP_SCRIPTLET_PATTERN = Pattern.compile("<%([^@!][^%>]*)%>");
    private static final Pattern JSP_EXPRESSION_PATTERN = Pattern.compile("<%=([^%>]*)%>");
    private static final Pattern JSP_DECLARATION_PATTERN = Pattern.compile("<%!([^%>]*)%>");
    private static final Pattern JSP_COMMENT_PATTERN = Pattern.compile("<%--([^-]|[-][^-]|[-][-][^%])*--%>");
    private static final Pattern EL_EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");
    private static final Pattern JSP_TAG_PATTERN = Pattern.compile("<(\\w+:[\\w-]+)([^>]*)(/?)>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<(\\w+)([^>]*)(/??)>");
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("<%@\\s*include\\s+file\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern TAGLIB_PATTERN = Pattern.compile("<%@\\s*taglib\\s+([^%>]*)%>");
    
    public JSPParseTool() {
        super("jsp-parse-tool", 
              "Parse and analyze JSP files. Extract directives, scriptlets, expressions, tags, and dependencies.",
              "parsing");
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = createBaseSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        ArrayNode required = (ArrayNode) schema.get("required");
        
        // JSP file path or directory
        ObjectNode sourceProperty = objectMapper.createObjectNode();
        sourceProperty.put("type", "string");
        sourceProperty.put("description", "JSP file path or directory to parse");
        properties.set("source", sourceProperty);
        required.add("source");
        
        // Analysis type
        ObjectNode analysisTypeProperty = objectMapper.createObjectNode();
        analysisTypeProperty.put("type", "string");
        analysisTypeProperty.put("description", "Type of analysis to perform");
        analysisTypeProperty.put("default", "full");
        ArrayNode analysisEnum = objectMapper.createArrayNode();
        analysisEnum.add("full").add("structure").add("dependencies").add("tags").add("expressions");
        analysisTypeProperty.set("enum", analysisEnum);
        properties.set("analysisType", analysisTypeProperty);
        
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
        
        // Extract content
        ObjectNode extractContentProperty = objectMapper.createObjectNode();
        extractContentProperty.put("type", "boolean");
        extractContentProperty.put("description", "Whether to extract actual content of elements");
        extractContentProperty.put("default", true);
        properties.set("extractContent", extractContentProperty);
        
        // Validate syntax
        ObjectNode validateSyntaxProperty = objectMapper.createObjectNode();
        validateSyntaxProperty.put("type", "boolean");
        validateSyntaxProperty.put("description", "Whether to validate JSP syntax");
        validateSyntaxProperty.put("default", false);
        properties.set("validateSyntax", validateSyntaxProperty);
        
        return schema;
    }
    
    @Override
    protected McpToolResult doExecute(Map<String, Object> parameters) throws McpToolException {
        String source = getRequiredParameter(parameters, "source", String.class);
        String analysisType = getOptionalParameter(parameters, "analysisType", "full", String.class);
        boolean extractContent = getOptionalParameter(parameters, "extractContent", true, Boolean.class);
        boolean validateSyntax = getOptionalParameter(parameters, "validateSyntax", false, Boolean.class);
        
        validateNotEmpty(source, "source");
        
        try {
            Path sourcePath = Paths.get(source);
            if (!Files.exists(sourcePath)) {
                throw new McpToolException(getName(), "SOURCE_NOT_FOUND", 
                        "Source path not found: " + source);
            }
            
            List<Path> jspFiles = collectJspFiles(sourcePath, parameters);
            if (jspFiles.isEmpty()) {
                throw new McpToolException(getName(), "NO_JSP_FILES", 
                        "No JSP files found in source: " + source);
            }
            
            Map<String, Object> analysisResult = new HashMap<>();
            List<Map<String, Object>> fileAnalyses = new ArrayList<>();
            
            for (Path jspFile : jspFiles) {
                Map<String, Object> fileAnalysis = analyzeJspFile(jspFile, analysisType, extractContent, validateSyntax);
                fileAnalyses.add(fileAnalysis);
            }
            
            analysisResult.put("files", fileAnalyses);
            analysisResult.put("totalFiles", jspFiles.size());
            analysisResult.put("analysisType", analysisType);
            
            // Generate summary
            Map<String, Object> summary = generateSummary(fileAnalyses);
            analysisResult.put("summary", summary);
            
            return McpToolResult.success("JSP analysis completed successfully", analysisResult);
            
        } catch (IOException e) {
            throw new McpToolException(getName(), "IO_ERROR", 
                    "Failed to read JSP files", e);
        }
    }
    
    private List<Path> collectJspFiles(Path sourcePath, Map<String, Object> parameters) throws IOException, McpToolException {
        @SuppressWarnings("unchecked")
        List<String> includePatterns = getOptionalParameter(parameters, "include", new ArrayList<>(), List.class);
        @SuppressWarnings("unchecked")
        List<String> excludePatterns = getOptionalParameter(parameters, "exclude", new ArrayList<>(), List.class);
        
        List<Path> jspFiles = new ArrayList<>();
        
        if (Files.isDirectory(sourcePath)) {
            Files.walk(sourcePath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspx"))
                    .filter(path -> matchesPatterns(path, includePatterns, excludePatterns))
                    .forEach(jspFiles::add);
        } else if (sourcePath.toString().endsWith(".jsp") || sourcePath.toString().endsWith(".jspx")) {
            jspFiles.add(sourcePath);
        }
        
        return jspFiles;
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
    
    private Map<String, Object> analyzeJspFile(Path jspFile, String analysisType, 
                                              boolean extractContent, boolean validateSyntax) throws IOException {
        String content = Files.readString(jspFile);
        Map<String, Object> analysis = new HashMap<>();
        
        analysis.put("file", jspFile.toString());
        analysis.put("size", content.length());
        analysis.put("lines", content.split("\n").length);
        
        switch (analysisType.toLowerCase()) {
            case "full":
                analysis.putAll(performFullAnalysis(content, extractContent));
                break;
            case "structure":
                analysis.putAll(analyzeStructure(content, extractContent));
                break;
            case "dependencies":
                analysis.putAll(analyzeDependencies(content));
                break;
            case "tags":
                analysis.putAll(analyzeTags(content, extractContent));
                break;
            case "expressions":
                analysis.putAll(analyzeExpressions(content, extractContent));
                break;
        }
        
        if (validateSyntax) {
            analysis.put("syntaxValidation", validateJspSyntax(content));
        }
        
        return analysis;
    }
    
    private Map<String, Object> performFullAnalysis(String content, boolean extractContent) {
        Map<String, Object> analysis = new HashMap<>();
        
        analysis.putAll(analyzeStructure(content, extractContent));
        analysis.putAll(analyzeDependencies(content));
        analysis.putAll(analyzeTags(content, extractContent));
        analysis.putAll(analyzeExpressions(content, extractContent));
        
        return analysis;
    }
    
    private Map<String, Object> analyzeStructure(String content, boolean extractContent) {
        Map<String, Object> structure = new HashMap<>();
        
        // Analyze directives
        List<Map<String, Object>> directives = new ArrayList<>();
        Matcher directiveMatcher = JSP_DIRECTIVE_PATTERN.matcher(content);
        while (directiveMatcher.find()) {
            Map<String, Object> directive = new HashMap<>();
            directive.put("type", directiveMatcher.group(1));
            directive.put("attributes", directiveMatcher.group(2).trim());
            if (extractContent) {
                directive.put("fullContent", directiveMatcher.group(0));
            }
            directives.add(directive);
        }
        structure.put("directives", directives);
        
        // Analyze scriptlets
        List<Map<String, Object>> scriptlets = new ArrayList<>();
        Matcher scriptletMatcher = JSP_SCRIPTLET_PATTERN.matcher(content);
        while (scriptletMatcher.find()) {
            Map<String, Object> scriptlet = new HashMap<>();
            if (extractContent) {
                scriptlet.put("code", scriptletMatcher.group(1).trim());
                scriptlet.put("fullContent", scriptletMatcher.group(0));
            }
            scriptlets.add(scriptlet);
        }
        structure.put("scriptlets", scriptlets);
        
        // Analyze declarations
        List<Map<String, Object>> declarations = new ArrayList<>();
        Matcher declarationMatcher = JSP_DECLARATION_PATTERN.matcher(content);
        while (declarationMatcher.find()) {
            Map<String, Object> declaration = new HashMap<>();
            if (extractContent) {
                declaration.put("code", declarationMatcher.group(1).trim());
                declaration.put("fullContent", declarationMatcher.group(0));
            }
            declarations.add(declaration);
        }
        structure.put("declarations", declarations);
        
        // Analyze comments
        List<Map<String, Object>> comments = new ArrayList<>();
        Matcher commentMatcher = JSP_COMMENT_PATTERN.matcher(content);
        while (commentMatcher.find()) {
            Map<String, Object> comment = new HashMap<>();
            if (extractContent) {
                comment.put("content", commentMatcher.group(1));
                comment.put("fullContent", commentMatcher.group(0));
            }
            comments.add(comment);
        }
        structure.put("comments", comments);
        
        return structure;
    }
    
    private Map<String, Object> analyzeDependencies(String content) {
        Map<String, Object> dependencies = new HashMap<>();
        
        // Analyze includes
        List<String> includes = new ArrayList<>();
        Matcher includeMatcher = INCLUDE_PATTERN.matcher(content);
        while (includeMatcher.find()) {
            includes.add(includeMatcher.group(1));
        }
        dependencies.put("includes", includes);
        
        // Analyze taglibs
        List<Map<String, Object>> taglibs = new ArrayList<>();
        Matcher taglibMatcher = TAGLIB_PATTERN.matcher(content);
        while (taglibMatcher.find()) {
            Map<String, Object> taglib = new HashMap<>();
            String attributes = taglibMatcher.group(1);
            
            // Parse taglib attributes
            Pattern uriPattern = Pattern.compile("uri\\s*=\\s*[\"']([^\"']*)[\"']");
            Pattern prefixPattern = Pattern.compile("prefix\\s*=\\s*[\"']([^\"']*)[\"']");
            
            Matcher uriMatcher = uriPattern.matcher(attributes);
            Matcher prefixMatcher = prefixPattern.matcher(attributes);
            
            if (uriMatcher.find()) {
                taglib.put("uri", uriMatcher.group(1));
            }
            if (prefixMatcher.find()) {
                taglib.put("prefix", prefixMatcher.group(1));
            }
            
            taglibs.add(taglib);
        }
        dependencies.put("taglibs", taglibs);
        
        return dependencies;
    }
    
    private Map<String, Object> analyzeTags(String content, boolean extractContent) {
        Map<String, Object> tags = new HashMap<>();
        
        // Analyze JSP tags
        List<Map<String, Object>> jspTags = new ArrayList<>();
        Matcher jspTagMatcher = JSP_TAG_PATTERN.matcher(content);
        while (jspTagMatcher.find()) {
            Map<String, Object> tag = new HashMap<>();
            tag.put("name", jspTagMatcher.group(1));
            tag.put("attributes", jspTagMatcher.group(2).trim());
            tag.put("selfClosing", !jspTagMatcher.group(3).isEmpty());
            if (extractContent) {
                tag.put("fullContent", jspTagMatcher.group(0));
            }
            jspTags.add(tag);
        }
        tags.put("jspTags", jspTags);
        
        // Analyze HTML tags
        List<Map<String, Object>> htmlTags = new ArrayList<>();
        Matcher htmlTagMatcher = HTML_TAG_PATTERN.matcher(content);
        while (htmlTagMatcher.find()) {
            Map<String, Object> tag = new HashMap<>();
            tag.put("name", htmlTagMatcher.group(1));
            tag.put("attributes", htmlTagMatcher.group(2).trim());
            tag.put("selfClosing", !htmlTagMatcher.group(3).isEmpty());
            if (extractContent) {
                tag.put("fullContent", htmlTagMatcher.group(0));
            }
            htmlTags.add(tag);
        }
        tags.put("htmlTags", htmlTags);
        
        return tags;
    }
    
    private Map<String, Object> analyzeExpressions(String content, boolean extractContent) {
        Map<String, Object> expressions = new HashMap<>();
        
        // Analyze JSP expressions
        List<Map<String, Object>> jspExpressions = new ArrayList<>();
        Matcher jspExprMatcher = JSP_EXPRESSION_PATTERN.matcher(content);
        while (jspExprMatcher.find()) {
            Map<String, Object> expr = new HashMap<>();
            if (extractContent) {
                expr.put("expression", jspExprMatcher.group(1).trim());
                expr.put("fullContent", jspExprMatcher.group(0));
            }
            jspExpressions.add(expr);
        }
        expressions.put("jspExpressions", jspExpressions);
        
        // Analyze EL expressions
        List<Map<String, Object>> elExpressions = new ArrayList<>();
        Matcher elExprMatcher = EL_EXPRESSION_PATTERN.matcher(content);
        while (elExprMatcher.find()) {
            Map<String, Object> expr = new HashMap<>();
            if (extractContent) {
                expr.put("expression", elExprMatcher.group(1).trim());
                expr.put("fullContent", elExprMatcher.group(0));
            }
            elExpressions.add(expr);
        }
        expressions.put("elExpressions", elExpressions);
        
        return expressions;
    }
    
    private Map<String, Object> validateJspSyntax(String content) {
        Map<String, Object> validation = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic syntax validation
        if (!content.contains("<%@ page")) {
            warnings.add("No page directive found");
        }
        
        // Check for unclosed tags
        long openScriptlets = content.split("<%").length - 1;
        long closeScriptlets = content.split("%>").length - 1;
        if (openScriptlets != closeScriptlets) {
            errors.add("Mismatched scriptlet tags: " + openScriptlets + " open, " + closeScriptlets + " close");
        }
        
        // Check for unclosed EL expressions
        long openEL = content.split("\\$\\{").length - 1;
        long closeEL = content.split("\\}").length - 1;
        if (openEL > closeEL) {
            errors.add("Unclosed EL expressions detected");
        }
        
        validation.put("errors", errors);
        validation.put("warnings", warnings);
        validation.put("valid", errors.isEmpty());
        
        return validation;
    }
    
    private Map<String, Object> generateSummary(List<Map<String, Object>> fileAnalyses) {
        Map<String, Object> summary = new HashMap<>();
        
        int totalDirectives = 0;
        int totalScriptlets = 0;
        int totalExpressions = 0;
        int totalTags = 0;
        Set<String> allTaglibs = new HashSet<>();
        
        for (Map<String, Object> analysis : fileAnalyses) {
            if (analysis.containsKey("directives")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> directives = (List<Map<String, Object>>) analysis.get("directives");
                totalDirectives += directives.size();
            }
            
            if (analysis.containsKey("scriptlets")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> scriptlets = (List<Map<String, Object>>) analysis.get("scriptlets");
                totalScriptlets += scriptlets.size();
            }
            
            if (analysis.containsKey("jspExpressions")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> expressions = (List<Map<String, Object>>) analysis.get("jspExpressions");
                totalExpressions += expressions.size();
            }
            
            if (analysis.containsKey("jspTags")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tags = (List<Map<String, Object>>) analysis.get("jspTags");
                totalTags += tags.size();
            }
            
            if (analysis.containsKey("taglibs")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> taglibs = (List<Map<String, Object>>) analysis.get("taglibs");
                for (Map<String, Object> taglib : taglibs) {
                    if (taglib.containsKey("uri")) {
                        allTaglibs.add((String) taglib.get("uri"));
                    }
                }
            }
        }
        
        summary.put("totalDirectives", totalDirectives);
        summary.put("totalScriptlets", totalScriptlets);
        summary.put("totalExpressions", totalExpressions);
        summary.put("totalTags", totalTags);
        summary.put("uniqueTaglibs", allTaglibs.size());
        summary.put("taglibUris", new ArrayList<>(allTaglibs));
        
        return summary;
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        return switch (operation.toLowerCase()) {
            case "jsp-parse", "jsp-analysis", "web-parsing", "jsp-structure" -> true;
            default -> false;
        };
    }
}
