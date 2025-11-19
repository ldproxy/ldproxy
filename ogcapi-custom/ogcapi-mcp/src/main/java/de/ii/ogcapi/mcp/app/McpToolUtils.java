/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import de.ii.ogcapi.collections.queryables.domain.QueryParameterTemplateQueryable;
import de.ii.ogcapi.features.search.domain.QueryParameterTemplateParameter;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.OgcApiResource;
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpConfiguration.McpIncludeExclude;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.HttpMethod;

public class McpToolUtils {

  private static String extractQueryId(String path) {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private static final String STORED_QUERY_PREFIX = "query_";

  public static class CollectionsResult {
    private final Map<String, ObjectSchema> collections;
    private final Map<String, List<OgcApiQueryParameter>> queryParametersByCollection;

    public CollectionsResult(
        Map<String, ObjectSchema> collections,
        Map<String, List<OgcApiQueryParameter>> queryParametersByCollection) {
      this.collections = collections;
      this.queryParametersByCollection = queryParametersByCollection;
    }

    public Map<String, ObjectSchema> getCollections() {
      return collections;
    }

    public Map<String, List<OgcApiQueryParameter>> getQueryParametersByCollection() {
      return queryParametersByCollection;
    }
  }

  public static class StoredQueriesResult {
    private final List<ImmutableMcpTool> tools;
    private final Map<String, List<OgcApiQueryParameter>> parametersByQuery;

    public StoredQueriesResult(
        List<ImmutableMcpTool> tools, Map<String, List<OgcApiQueryParameter>> parametersByQuery) {
      this.tools = tools;
      this.parametersByQuery = parametersByQuery;
    }

    public List<ImmutableMcpTool> getTools() {
      return tools;
    }

    public Map<String, List<OgcApiQueryParameter>> getParametersByQuery() {
      return parametersByQuery;
    }
  }

  public static StoredQueriesResult filterAndCreateStoredQueries(
      List<StoredQueryExpression> storedQueries,
      McpConfiguration mcpConfiguration,
      ExtensionRegistry extensionRegistry,
      OgcApiDataV2 apiData) {

    List<ApiEndpointDefinition> definitions =
        extensionRegistry.getExtensionsForType(EndpointExtension.class).stream()
            .filter(endpoint -> endpoint.isEnabledForApi(apiData))
            .map(endpoint -> endpoint.getDefinition(apiData))
            .filter(
                def ->
                    def.getResources().values().stream()
                        .flatMap(res -> res.getOperations().values().stream())
                        .anyMatch(op -> op.getOperationId().contains("executeStoredQuery")))
            .toList();

    Optional<McpIncludeExclude> includedOpt = mcpConfiguration.getIncluded();
    Optional<McpIncludeExclude> excludedOpt = mcpConfiguration.getExcluded();

    List<String> includedQueries = includedOpt.map(McpIncludeExclude::getQueries).orElse(null);
    List<String> excludedQueries = excludedOpt.map(McpIncludeExclude::getQueries).orElse(List.of());

    List<StoredQueryExpression> filteredQueries;

    if ((includedQueries == null || includedOpt.isEmpty()) && excludedQueries.isEmpty()) {
      filteredQueries = storedQueries;
    } else if (includedQueries == null) {
      filteredQueries = List.of();
    } else if (includedQueries.contains("*")) {
      filteredQueries =
          storedQueries.stream()
              .filter(
                  q ->
                      !excludedQueries.contains(q.getId())
                          && !excludedQueries.contains(q.getTitle().orElse("")))
              .toList();
    } else {
      filteredQueries =
          storedQueries.stream()
              .filter(
                  q ->
                      includedQueries.contains(q.getId())
                          || includedQueries.contains(q.getTitle().orElse("")))
              .filter(
                  q ->
                      !excludedQueries.contains(q.getId())
                          && !excludedQueries.contains(q.getTitle().orElse("")))
              .toList();
    }

    Map<String, OgcApiResource> resourceMapByQueryId =
        definitions.stream()
            .flatMap(def -> def.getResources().values().stream())
            .filter(
                resource -> {
                  String queryId = extractQueryId(resource.getPath());
                  return filteredQueries.stream().anyMatch(query -> query.getId().equals(queryId));
                })
            .collect(
                Collectors.toMap(
                    resource -> extractQueryId(resource.getPath()), resource -> resource));

    Map<String, List<OgcApiQueryParameter>> queryParametersByQuery =
        resourceMapByQueryId.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        entry.getValue().getOperations().values().stream()
                            .flatMap(op -> op.getQueryParameters().stream())
                            .filter(
                                param ->
                                    "offset".equals(param.getName())
                                        || (param instanceof QueryParameterTemplateParameter
                                            && Objects.equals(
                                                ((QueryParameterTemplateParameter) param)
                                                    .getQueryId(),
                                                entry.getKey())))
                            .toList()));

    List<ImmutableMcpTool> tools =
        queryParametersByQuery.entrySet().stream()
            .map(
                entry -> {
                  String queryId = entry.getKey();
                  List<OgcApiQueryParameter> parameters = entry.getValue();

                  ObjectSchema inputSchema = new ObjectSchema();
                  parameters.forEach(
                      param -> {
                        Schema<?> schema = param.getSchema(apiData, Optional.empty());
                        if (schema != null) {
                          inputSchema.addProperty(param.getName(), schema);
                        }
                      });

                  if (inputSchema.getProperties() == null
                      || inputSchema.getProperties().isEmpty()) {
                    throw new IllegalStateException(
                        "Cannot create McpTool for queryId "
                            + queryId
                            + ": inputSchema is missing");
                  }

                  return new ImmutableMcpTool.Builder()
                      .id(STORED_QUERY_PREFIX + queryId)
                      .name("Stored Query - " + queryId)
                      .description("Tool for stored query: " + queryId)
                      .inputSchema(inputSchema)
                      .queryParameters(parameters)
                      .build();
                })
            .toList();

    return new StoredQueriesResult(tools, queryParametersByQuery);
  }

  public static CollectionsResult filterAndCreateCollections(
      McpConfiguration mcpConfiguration,
      OgcApiDataV2 apiData,
      ExtensionRegistry extensionRegistry,
      List<String> allowedNames) {

    List<ApiEndpointDefinition> definitions =
        extensionRegistry.getExtensionsForType(EndpointExtension.class).stream()
            .filter(endpoint -> endpoint.isEnabledForApi(apiData))
            .map(endpoint -> endpoint.getDefinition(apiData))
            .filter(
                def ->
                    def.getResources().values().stream()
                        .flatMap(res -> res.getOperations().values().stream())
                        .anyMatch(op -> op.getOperationId().contains("getItems")))
            .toList();

    Optional<McpIncludeExclude> includedOpt = mcpConfiguration.getIncluded();
    Optional<McpIncludeExclude> excludedOpt = mcpConfiguration.getExcluded();

    List<String> includedCollections =
        includedOpt.map(McpIncludeExclude::getCollections).orElse(null);
    List<String> excludedCollections =
        excludedOpt.map(McpIncludeExclude::getCollections).orElse(List.of());

    Map<String, OgcApiResource> collectionMap =
        definitions.stream()
            .flatMap(def -> def.getResources().values().stream())
            .map(res -> Map.entry(res.getCollectionId(apiData).orElse(""), res))
            .filter(
                e -> {
                  String id = e.getKey();

                  boolean included;
                  if (includedCollections == null) included = false;
                  else if (includedCollections.contains("*")) included = true;
                  else included = includedCollections.contains(id);

                  boolean excluded = excludedCollections.contains(id);

                  return included && !excluded;
                })
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    // Map: CollectionId -> List of filtered query parameters
    Map<String, List<OgcApiQueryParameter>> queryParametersByCollection =
        definitions.stream()
            .flatMap(def -> def.getResources().values().stream())
            .filter(res -> collectionMap.containsKey(res.getCollectionId(apiData).orElse("")))
            .collect(
                Collectors.toMap(
                    res -> res.getCollectionId(apiData).orElse(""),
                    res ->
                        res.getOperations().values().stream()
                            .flatMap(op -> op.getQueryParameters().stream())
                            .filter(
                                param ->
                                    allowedNames == null
                                        || allowedNames.contains(param.getName())
                                        || param instanceof QueryParameterTemplateQueryable)
                            .collect(Collectors.toList())));

    //  System.out.println("queryParametersByCollection" + queryParametersByCollection);

    Map<String, Schema<?>> globalParameters = new HashMap<>();

    for (ApiEndpointDefinition def : definitions) {
      for (OgcApiResource resource : def.getResources().values()) {
        ApiOperation getOp = resource.getOperations().get(HttpMethod.GET);
        if (getOp == null) continue;

        for (OgcApiQueryParameter param : getOp.getQueryParameters()) {
          String name = param.getName();
          if ("bbox".equals(name)
              || "datetime".equals(name)
              || "offset".equals(name)
              || "limit".equals(name)
              || "sortby".equals(name)) {
            Object schema = param.getSchema(apiData, Optional.empty());

            if (schema instanceof Schema<?> s) {
              globalParameters.put(name, s);
            }
          }
        }
      }
    }

    Map<String, ObjectSchema> collections =
        collectionMap.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    e -> {
                      Map<String, Schema<?>> props = new HashMap<>(globalParameters);
                      List<OgcApiQueryParameter> params =
                          queryParametersByCollection.getOrDefault(e.getKey(), List.of());
                      for (OgcApiQueryParameter param : params) {
                        Schema<?> schema = param.getSchema(apiData, Optional.empty());
                        if (schema != null) {
                          props.put(param.getName(), schema);
                        }
                      }
                      ObjectSchema schema = new ObjectSchema();
                      schema.setProperties((Map) props);
                      return schema;
                    }));

    for (Entry<String, List<OgcApiQueryParameter>> entry : queryParametersByCollection.entrySet()) {
      ObjectSchema objectSchema = new ObjectSchema();
      for (OgcApiQueryParameter param : entry.getValue()) {
        Schema<?> schema = param.getSchema(apiData, Optional.empty());
        if (schema != null) {
          schema.setDescription(param.getDescription());
          objectSchema.addProperty(param.getName(), schema);
        }
      }
      collections.put(entry.getKey(), objectSchema);
    }

    // System.out.println("queryParametersByCollection" + queryParametersByCollection);

    return new CollectionsResult(collections, queryParametersByCollection);
  }
}
