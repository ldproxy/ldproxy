/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.search.domain.ImmutableQueryExpression;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputQuery;
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression;
import de.ii.ogcapi.features.search.domain.ParameterResolver;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.QueryExpressionQueryParameter;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ApiRequestAuthorizer;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpTool;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.cql.domain.Cql;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The stored queries, a tool per stored query. */
final class McpToolsStoredQueries implements McpToolSource {

  static final String PREFIX = "query_";

  private final AppContext appContext;
  private final ExtensionRegistry extensionRegistry;
  private final StoredQueryRepository storedQueryRepository;
  private final FeaturesCoreProviders providers;
  private final SearchQueriesHandler searchQueriesHandler;
  private final Cql cql;
  private final ApiRequestAuthorizer apiRequestAuthorizer;
  private final ObjectMapper mcpObjectMapper;

  McpToolsStoredQueries(
      AppContext appContext,
      ExtensionRegistry extensionRegistry,
      StoredQueryRepository storedQueryRepository,
      FeaturesCoreProviders providers,
      SearchQueriesHandler searchQueriesHandler,
      Cql cql,
      ApiRequestAuthorizer apiRequestAuthorizer,
      ObjectMapper mcpObjectMapper) {
    this.appContext = appContext;
    this.extensionRegistry = extensionRegistry;
    this.storedQueryRepository = storedQueryRepository;
    this.providers = providers;
    this.searchQueriesHandler = searchQueriesHandler;
    this.cql = cql;
    this.apiRequestAuthorizer = apiRequestAuthorizer;
    this.mcpObjectMapper = mcpObjectMapper;
  }

  @Override
  public String getPrefix() {
    return PREFIX;
  }

  @Override
  public List<McpTool> getTools(OgcApi api, McpConfiguration configuration) {
    return McpToolUtils.filterAndCreateStoredQueries(
        storedQueryRepository.getAll(api.getData()),
        configuration,
        extensionRegistry,
        api.getData());
  }

  // the stored queries can change without a change of the API, for example when they are reloaded
  @Override
  public int getFingerprint(OgcApi api) {
    return storedQueryRepository.getAll(api.getData()).hashCode();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> call(
      OgcApi api,
      McpTool tool,
      Map<String, Object> arguments,
      Optional<ApiRequestContext> mcpRequest)
      throws IOException {
    String queryId = tool.getId().substring(PREFIX.length());
    McpInternalRequests.checkArguments(tool, arguments, Optional.empty());
    String result = handleStoredQuery(api, mcpRequest, tool, queryId, arguments);

    return McpServerImpl.forToolResult(mcpObjectMapper.readValue(result, Map.class));
  }

  private String handleStoredQuery(
      OgcApi api,
      Optional<ApiRequestContext> mcpRequest,
      McpTool tool,
      String queryId,
      Map<String, Object> parameters)
      throws IOException {

    OgcApiDataV2 apiData = api.getData();
    List<OgcApiQueryParameter> queryParameters = tool.getQueryParameters();

    Map<String, String> stringParams =
        McpInternalRequests.queryParameterValues(
            parameters, queryParameters, param -> param.getSchema(apiData, Optional.empty()));

    QueryParameterSet queryParameterSet = QueryParameterSet.of(queryParameters, stringParams);
    queryParameterSet = queryParameterSet.evaluate(api, Optional.empty());

    // the same permissions as a request to the stored query; the query is derived from the
    // parameters of the request that is returned, a policy may have added some
    ApiRequestContext requestContext =
        McpInternalRequests.internalRequest(
                appContext, api, mcpRequest, stringParams, "search", queryId)
            .method("GET")
            .mediaType(McpInternalRequests.GEOJSON)
            .alternateMediaTypes(
                McpInternalRequests.alternateMediaTypes(tool, McpInternalRequests.GEOJSON))
            .queryParameterSet(queryParameterSet)
            .build();
    requestContext =
        McpInternalRequests.authorize(apiRequestAuthorizer, tool, requestContext, Optional.empty());
    queryParameterSet = requestContext.getQueryParameterSet();

    StoredQueryExpression storedQuery = storedQueryRepository.get(apiData, queryId);
    ImmutableStoredQueryExpression.Builder builder =
        new ImmutableStoredQueryExpression.Builder().from(storedQuery);
    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof QueryExpressionQueryParameter) {
        ((QueryExpressionQueryParameter) parameter).applyTo(builder, queryParameterSet);
      }
    }

    storedQuery = builder.build();

    FeaturesCoreConfiguration coreConfiguration =
        apiData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();

    SchemaValidator schemaValidator =
        queryParameterSet.getDefinitions().stream()
            .map(
                param -> {
                  try {
                    java.lang.reflect.Field f =
                        param.getClass().getDeclaredField("schemaValidator");
                    f.setAccessible(true);
                    return (SchemaValidator) f.get(param);
                  } catch (Exception e) {
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    if (schemaValidator == null) {
      throw new IllegalStateException("No SchemaValidator found!");
    }

    QueryExpression executableQuery =
        new ParameterResolver(queryParameterSet, schemaValidator, cql).visit(storedQuery);

    // references without their URI, unless the stored query selects how references are written
    boolean referencesSelected =
        Profile.of(extensionRegistry, executableQuery.getProfiles().toArray(String[]::new)).stream()
            .anyMatch(profile -> ProfileRelAsKeyTitle.PROFILE_SET.equals(profile.getProfileSet()));
    if (!referencesSelected) {
      executableQuery =
          new ImmutableQueryExpression.Builder()
              .from(executableQuery)
              .addProfiles(ProfileRelAsKeyTitle.ID)
              .build();
    }

    SearchQueriesHandler.QueryInputQuery queryInput =
        new ImmutableQueryInputQuery.Builder()
            .query(executableQuery)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .minimumPageSize(Optional.ofNullable(coreConfiguration.getMinimumPageSize()))
            .defaultPageSize(Optional.ofNullable(coreConfiguration.getDefaultPageSize()))
            .maximumPageSize(Optional.ofNullable(coreConfiguration.getMaximumPageSize()))
            .isStoredQuery(true)
            .build();

    try (Response response =
        searchQueriesHandler.handle(SearchQueriesHandler.Query.QUERY, queryInput, requestContext)) {
      Object entity = response.getEntity();

      if (entity instanceof StreamingOutput) {
        StreamingOutput streamingOutput = (StreamingOutput) entity;
        OutputStream os = new ByteArrayOutputStream();
        streamingOutput.write(os);
        return os.toString();
      }

      return entity instanceof byte[] ? new String((byte[]) entity) : "";
    }
  }
}
