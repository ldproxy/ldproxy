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
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.foundation.domain.ApiRequestAuthorizer;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpTool;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The features of the collections, a tool per collection. */
final class McpToolsCollections implements McpToolSource {

  static final String PREFIX = "collection_";

  // the query parameters of the features that are arguments of a tool, besides the queryables
  private static final List<String> PARAMETERS =
      List.of(
          "bbox",
          "datetime",
          "ids",
          "limit",
          "offset",
          "sortby",
          "properties",
          "exclude-properties",
          "filter",
          "zoom-level");

  private final AppContext appContext;
  private final ExtensionRegistry extensionRegistry;
  private final FeaturesCoreProviders providers;
  private final FeaturesQuery ogcApiFeaturesQuery;
  private final FeaturesCoreQueriesHandler featuresCoreQueriesHandler;
  private final ApiRequestAuthorizer apiRequestAuthorizer;
  private final ObjectMapper mcpObjectMapper;

  McpToolsCollections(
      AppContext appContext,
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      FeaturesQuery ogcApiFeaturesQuery,
      FeaturesCoreQueriesHandler featuresCoreQueriesHandler,
      ApiRequestAuthorizer apiRequestAuthorizer,
      ObjectMapper mcpObjectMapper) {
    this.appContext = appContext;
    this.extensionRegistry = extensionRegistry;
    this.providers = providers;
    this.ogcApiFeaturesQuery = ogcApiFeaturesQuery;
    this.featuresCoreQueriesHandler = featuresCoreQueriesHandler;
    this.apiRequestAuthorizer = apiRequestAuthorizer;
    this.mcpObjectMapper = mcpObjectMapper;
  }

  @Override
  public String getPrefix() {
    return PREFIX;
  }

  @Override
  public List<McpTool> getTools(OgcApi api, McpConfiguration configuration) {
    return McpToolUtils.filterAndCreateCollections(
        configuration, api, extensionRegistry, PARAMETERS);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> call(
      OgcApi api,
      McpTool tool,
      Map<String, Object> arguments,
      Optional<ApiRequestContext> mcpRequest)
      throws IOException {
    String collectionId = tool.getId().substring(PREFIX.length());
    McpInternalRequests.checkArguments(
        tool,
        arguments,
        Optional.of(
            "Other queryables of the collection can be used in the argument 'filter', see its"
                + " description."));
    String result = handleCollectionQuery(api, mcpRequest, tool, collectionId, arguments);

    return McpServerImpl.forToolResult(mcpObjectMapper.readValue(result, Map.class));
  }

  private String handleCollectionQuery(
      OgcApi api,
      Optional<ApiRequestContext> mcpRequest,
      McpTool tool,
      String collectionId,
      Map<String, Object> parameters) {
    OgcApiDataV2 apiData = api.getData();
    List<OgcApiQueryParameter> queryParameters = tool.getQueryParameters();

    Map<String, String> stringParams =
        McpInternalRequests.queryParameterValues(
            parameters, queryParameters, param -> param.getSchema(apiData, collectionId));

    QueryParameterSet parameterSet = QueryParameterSet.of(queryParameters, stringParams);

    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    parameterSet = parameterSet.evaluate(api, Optional.of(collectionData));

    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(apiData, collectionData);

    FeaturesCoreConfiguration coreConfiguration =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .filter(
                cfg ->
                    cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                        != FeaturesCoreConfiguration.ItemType.unknown)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format(
                            "Features are not supported in collection '%s'.", collectionId)));

    // the same permissions as a request to the features of the collection; the query is derived
    // from the parameters of the request that is returned, a policy may have added some
    ApiRequestContext requestContext =
        McpInternalRequests.internalRequest(
                appContext, api, mcpRequest, stringParams, "collections", collectionId, "items")
            .method("GET")
            .mediaType(McpInternalRequests.GEOJSON)
            .alternateMediaTypes(
                McpInternalRequests.alternateMediaTypes(tool, McpInternalRequests.GEOJSON))
            .queryParameterSet(parameterSet)
            .build();
    requestContext =
        McpInternalRequests.authorize(apiRequestAuthorizer, tool, requestContext, Optional.empty());
    parameterSet = requestContext.getQueryParameterSet();

    int defaultPageSize = coreConfiguration.getDefaultPageSize();

    FeatureQuery query =
        ogcApiFeaturesQuery.requestToFeatureQuery(
            apiData,
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            defaultPageSize,
            parameterSet,
            false);

    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        new ImmutableQueryInputFeatures.Builder()
            .collectionId(collectionId)
            .query(query)
            .featureProvider(featureProvider)
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .defaultPageSize(Optional.of(defaultPageSize))
            .profiles(Profile.of(extensionRegistry, ProfileRelAsKeyTitle.ID))
            .sendResponseAsStream(false)
            .build();

    try (Response response =
        featuresCoreQueriesHandler.handle(
            FeaturesCoreQueriesHandler.Query.FEATURES, queryInput, requestContext)) {

      Object entity = response.getEntity();

      return entity instanceof byte[] ? new String((byte[]) entity) : "";
    }
  }
}
