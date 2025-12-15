/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import static de.ii.ogcapi.foundation.domain.ApiSecurity.GROUP_DISCOVER_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpServer;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import javax.ws.rs.core.Context;

/**
 * @title MCP
 * @path mcp
 * @langEn MCP server endpoint, can be added to AI tools as connector.
 * @langDe MCP Server-Endpunkt, kann KI-Werkzeugen als Konnektor hinzugefügt werden.
 */
@Singleton
@AutoBind
public class EndpointMcp extends Endpoint {

  private static final List<String> TAGS = ImmutableList.of("MCP");

  private final McpServer mcpServer;

  @Inject
  public EndpointMcp(McpServer mcpServer, ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
    this.mcpServer = mcpServer;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return McpConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    return List.of(FormatJson.INSTANCE);
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("mcp")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_CONFORMANCE);
    String path = "/mcp";
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    String operationSummary = "MCP server";
    Optional<String> operationDescription = Optional.of("Can be added to AI tools as connector.");
    ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
        new ImmutableOgcApiResourceAuxiliary.Builder().path(path);
    ApiOperation.getResource(
            apiData,
            path,
            false,
            queryParameters,
            ImmutableList.of(),
            getResponseContent(apiData),
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("postMcp"),
            GROUP_DISCOVER_READ,
            TAGS,
            McpBuildingBlock.MATURITY,
            McpBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations("POST", operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  @Override
  public boolean skipBodyParsing() {
    return true;
  }

  @POST
  public void executeMcpRequest(
      @Context OgcApi api,
      @Context HttpServletRequest request,
      @Context HttpServletResponse response)
      throws ServletException, IOException {
    mcpServer.getServlet(api).service(request, response);
  }
}
