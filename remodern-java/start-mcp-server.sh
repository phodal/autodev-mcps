#!/bin/bash

# Start the ReModern MCP Server
# This script starts the MCP server using STDIO transport

# Set the main class for MCP server
export SPRING_MAIN_CLASS=com.phodal.remodern.mcp.ReModernMcpServer

# Run the MCP server
java -Dspring.main.class=com.phodal.remodern.mcp.ReModernMcpServer \
     -jar target/remodern-java-1.0.0-SNAPSHOT.jar
