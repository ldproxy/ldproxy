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
import de.ii.ogcapi.common.domain.ConformanceDeclarationFormatExtension;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
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
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @title Model Context Protocol (MCP) Schema
 * @path mcp/schema
 * @langEn TODO
 * @langDe TODO
 */
@Singleton
@AutoBind
public class EndpointMcpSchema extends Endpoint {

  private static final List<String> TAGS = ImmutableList.of("MCP");

  private final McpServer mcpServer;
  private final StoredQueryRepository storedQueryRepository;

  @Inject
  public EndpointMcpSchema(
      McpServer mcpServer,
      ExtensionRegistry extensionRegistry,
      StoredQueryRepository storedQueryRepository) {
    super(extensionRegistry);
    this.mcpServer = mcpServer;
    this.storedQueryRepository = storedQueryRepository;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return McpConfiguration.class;
  }

  // TODO: az
  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(ConformanceDeclarationFormatExtension.class);
    }
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("mcp")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_CONFORMANCE);
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, "/mcp/schema");
    String operationSummary = "mcp declaration";
    Optional<String> operationDescription =
        Optional.of(
            "The URIs of all mcp classes supported by the server. "
                + "This information is provided to support 'generic' clients that want to access multiple "
                + "OGC API implementations - and not 'just' a specific API. For clients accessing only a single "
                + "API, this information is in general not relevant and the OpenAPI definition details the "
                + "required information about the API.");
    String path = "/mcp/schema";
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
            getOperationId("getMcpDeclaration"),
            GROUP_DISCOVER_READ,
            TAGS,
            McpBuildingBlock.MATURITY,
            McpBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  @Path("/schema")
  @GET
  @Produces({"application/json"})
  public Response getMcpClasses(@Context OgcApi api, @Context ApiRequestContext requestContext) {
    return Response.ok(mcpServer.getSchema(api)).build();
  }
}
