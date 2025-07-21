# ReModern Java

A comprehensive MCP (Model Context Protocol) toolset for Java development, providing code generation, parsing, migration, and analysis capabilities.

## Features

### MCP Tools

- **AstCodeGenTool**: Generate Java code using JavaPoet AST manipulation
- **TemplateCodeGenTool**: Template-driven code generation using FreeMarker
- **OpenRewriteTool**: AST migration and refactoring using OpenRewrite
- **JSPParseTool**: Parse and analyze JSP files
- **ByteCodeTool**: Bytecode analysis using ASM
- **JavaParseTool**: Java source code parsing and analysis

### Interface Layers

- **CLI Interface**: Command-line interface using Picocli
- **MCP Server**: Model Context Protocol server implementation using [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

### Building

```bash
mvn clean install
```

### Running Tests

```bash
mvn test
```

## Usage

### Command Line Interface

#### List Available Tools

```bash
java -jar target/remodern-java-1.0.0-SNAPSHOT.jar list
```

#### Get Tool Information

```bash
java -jar target/remodern-java-1.0.0-SNAPSHOT.jar info ast-code-gen
```

#### Run a Tool

```bash
# Generate a Java class
java -jar target/remodern-java-1.0.0-SNAPSHOT.jar run ast-code-gen \
  -p type=class \
  -p name=MyClass \
  -p packageName=com.example \
  -p outputDir=src/main/java

# Parse Java source files
java -jar target/remodern-java-1.0.0-SNAPSHOT.jar run java-parse-tool \
  -p source=src/main/java \
  -p analysisType=full

# Analyze bytecode
java -jar target/remodern-java-1.0.0-SNAPSHOT.jar run bytecode-tool \
  -p source=target/classes \
  -p analysisType=full
```

### MCP Server

Start the MCP server:

```bash
java -cp target/remodern-java-1.0.0-SNAPSHOT.jar com.phodal.remodern.mcp.ReModernMcpServer
```

The server will start and listen for MCP protocol messages via STDIO.

## Tool Documentation

### AstCodeGenTool

Generate Java code using JavaPoet AST manipulation.

**Parameters:**
- `type` (required): Type of code element (`class`, `interface`, `enum`, `method`, `field`)
- `name` (required): Name of the code element
- `packageName`: Package name for generated code
- `outputDir`: Output directory (default: `src/main/java`)
- `modifiers`: Access modifiers (`public`, `private`, `protected`, `static`, `final`, etc.)
- `superclass`: Superclass name (for classes)
- `interfaces`: List of interface names to implement
- `fields`: List of field definitions
- `methods`: List of method definitions

**Example:**
```json
{
  "type": "class",
  "name": "User",
  "packageName": "com.example.model",
  "modifiers": ["public"],
  "fields": [
    {
      "name": "id",
      "type": "Long",
      "modifiers": ["private"]
    }
  ],
  "methods": [
    {
      "name": "getId",
      "returnType": "Long",
      "modifiers": ["public"],
      "body": "return this.id"
    }
  ]
}
```

### TemplateCodeGenTool

Generate code using FreeMarker templates.

**Parameters:**
- `template` (required): Template content or file path
- `templateType`: `content` or `file` (default: `content`)
- `dataModel` (required): Data model for template processing
- `outputFile`: Output file path
- `templateDir`: Template directory (default: `templates`)

**Example:**
```json
{
  "template": "package ${packageName};\n\npublic class ${className} {\n}",
  "templateType": "content",
  "dataModel": {
    "packageName": "com.example",
    "className": "GeneratedClass"
  },
  "outputFile": "src/main/java/com/example/GeneratedClass.java"
}
```

### JavaParseTool

Parse and analyze Java source code.

**Parameters:**
- `source` (required): Java file or directory path
- `analysisType`: Type of analysis (`full`, `structure`, `methods`, `fields`, `imports`, `dependencies`)
- `includeMethodBodies`: Include method body analysis (default: `false`)
- `includeComments`: Include comments in analysis (default: `true`)

**Example:**
```json
{
  "source": "src/main/java",
  "analysisType": "full",
  "includeMethodBodies": true
}
```

### JSPParseTool

Parse and analyze JSP files.

**Parameters:**
- `source` (required): JSP file or directory path
- `analysisType`: Type of analysis (`full`, `structure`, `dependencies`, `tags`, `expressions`)
- `extractContent`: Extract actual content of elements (default: `true`)
- `validateSyntax`: Validate JSP syntax (default: `false`)

### ByteCodeTool

Analyze Java bytecode using ASM.

**Parameters:**
- `source` (required): `.class` file, `.jar` file, or directory path
- `analysisType`: Type of analysis (`full`, `structure`, `methods`, `fields`, `dependencies`, `instructions`)
- `includeMethodBytecode`: Include detailed method bytecode (default: `false`)
- `includeDebug`: Include debug information (default: `true`)
- `analyzeDependencies`: Analyze class dependencies (default: `true`)

### OpenRewriteTool

Perform AST migration and refactoring.

**Parameters:**
- `operation` (required): Operation type (`recipe`, `visitor`, `refactor`, `migrate`)
- `source` (required): Source file or directory path
- `recipe`: Recipe name (for recipe operation)
- `visitor`: Custom visitor class (for visitor operation)
- `rules`: List of refactoring rules (for refactor operation)
- `migrationTarget`: Target framework/version (for migrate operation)
- `dryRun`: Perform dry run without changes (default: `false`)

## Architecture

The project follows a modular architecture:

```
com.phodal.remodern/
├── core/                 # Core interfaces and utilities
│   ├── McpTool          # Base tool interface
│   ├── McpToolResult    # Tool execution result
│   ├── McpToolException # Tool exception handling
│   └── McpToolRegistry  # Tool registration and management
├── tools/               # Tool implementations
│   ├── codegen/         # Code generation tools
│   ├── parsing/         # Parsing tools
│   ├── migration/       # Migration tools
│   └── bytecode/        # Bytecode analysis tools
├── cli/                 # Command-line interface
├── mcp/                 # MCP server implementation
└── integration/         # Integration tests
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
