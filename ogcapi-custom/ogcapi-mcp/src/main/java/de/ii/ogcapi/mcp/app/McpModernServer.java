/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import de.ii.ogcapi.mcp.app.McpServerImpl.AvailableTool;
import de.ii.ogcapi.mcp.app.McpServerImpl.ToolCallResult;
import de.ii.ogcapi.mcp.app.McpServerImpl.ToolCaller;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.ResourceLink;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.McpSchema.ToolAnnotations;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The MCP server of the protocol revision 2026-07-28 for an API: without sessions and without an
 * initialize handshake, every request declares its protocol version and the capabilities of the
 * client. It offers the same tools as the legacy server; the SDK has separate types for this
 * revision, the tools and results are converted here.
 */
final class McpModernServer {

  private McpModernServer() {}

  static HttpServletMcpTransport create(
      io.modelcontextprotocol.spec.McpSchema.Implementation serverInfo,
      String instructions,
      List<AvailableTool> tools,
      ToolCaller toolCaller,
      McpJsonMapper jsonMapper,
      JsonSchemaValidator jsonSchemaValidator,
      McpTransportContextExtractor<HttpServletRequest> contextExtractor) {
    Map<String, McpSyncTool> toolsByName = new LinkedHashMap<>();
    for (AvailableTool tool : tools) {
      toolsByName.put(
          tool.tool().getId(),
          McpSyncTool.of(
              definition(tool),
              (context, request) ->
                  McpSyncResponse.result(
                      toResult(
                          toolCaller.call(
                              tool,
                              Objects.requireNonNullElse(request.arguments(), Map.of()),
                              context.transportContext())))));
    }
    List<Tool> definitions = toolsByName.values().stream().map(McpSyncTool::definition).toList();

    McpSyncToolRepository repository =
        new McpSyncToolRepository() {
          // all tools fit on one page
          @Override
          public ToolsPage list(McpRequestContext context, String cursor) {
            if (Objects.nonNull(cursor)) {
              throw McpException.invalidParams("Unknown cursor: " + cursor);
            }
            return ToolsPage.of(definitions);
          }

          @Override
          public McpSyncTool find(McpRequestContext context, String name) {
            return toolsByName.get(name);
          }
        };

    McpServer server =
        McpServer.builder()
            .serverInfo(
                Implementation.builder(serverInfo.name(), serverInfo.version())
                    .title(serverInfo.title())
                    .websiteUrl(serverInfo.websiteUrl())
                    .build())
            .instructions(instructions)
            .jsonMapper(jsonMapper)
            // the tools change when the API or its stored queries change, a client does not
            // cache the list
            .feature(
                ToolsFeature.ofSync(
                    repository, jsonMapper, jsonSchemaValidator, 0L, CacheScope.PRIVATE))
            .build();

    return HttpServletMcpTransport.builder(server)
        .jsonMapper(jsonMapper)
        .endpoint("/mcp")
        .contextExtractor(contextExtractor)
        .build();
  }

  private static Tool definition(AvailableTool tool) {
    return Tool.builder(tool.tool().getId(), tool.inputSchema())
        .title(tool.tool().getName())
        .description(tool.tool().getDescription())
        .outputSchema(tool.outputSchema())
        .annotations(
            tool.tool().isReadOnly()
                ? new ToolAnnotations(tool.tool().getName(), true, false, true, false)
                : new ToolAnnotations(tool.tool().getName(), null, null, null, null))
        .build();
  }

  private static CallToolResult toResult(ToolCallResult result) {
    CallToolResult.Builder builder =
        new CallToolResult.Builder()
            .addContent(TextContent.builder(result.text()).build())
            .isError(result.isError());
    if (Objects.nonNull(result.structuredContent())) {
      builder.structuredContent(result.structuredContent());
    }
    result
        .resourceLink()
        .ifPresent(
            link ->
                builder.addContent(
                    ResourceLink.builder(link.name(), link.uri())
                        .title(link.title())
                        .description(link.description())
                        .mimeType(link.mimeType())
                        .build()));
    return builder.build();
  }
}
