/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import de.ii.ogcapi.collections.queryables.domain.QueryParameterTemplateQueryable;
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
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.HttpMethod;

public class McpToolUtils {

  private static final String STORED_QUERY_PREFIX = "query_";

  public static class CollectionsResult {
    private final Map<String, ObjectSchema> collections;
    private final List<QueryParameterTemplateQueryable> filteredItems;

    public CollectionsResult(
        Map<String, ObjectSchema> collections,
        List<QueryParameterTemplateQueryable> filteredItems) {
      this.collections = collections;
      this.filteredItems = filteredItems;
    }

    public Map<String, ObjectSchema> getCollections() {
      return collections;
    }

    public List<QueryParameterTemplateQueryable> getFilteredItems() {
      return filteredItems;
    }
  }

  public static List<ImmutableMcpTool> filterAndCreateStoredQueries(
      List<StoredQueryExpression> storedQueries, McpConfiguration mcpConfiguration) {

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
                          && !excludedQueries.contains(
                              q.getTitle() != null ? q.getTitle().orElse("") : ""))
              .toList();
    } else {
      filteredQueries =
          storedQueries.stream()
              .filter(
                  q ->
                      includedQueries.contains(q.getId())
                          || includedQueries.contains(
                              q.getTitle() != null ? q.getTitle().orElse("") : ""))
              .filter(
                  q ->
                      !excludedQueries.contains(q.getId())
                          && !excludedQueries.contains(
                              q.getTitle() != null ? q.getTitle().orElse("") : ""))
              .toList();
    }

    return filteredQueries.stream()
        .map(
            query -> {
              String queryId = query.getId() != null ? query.getId() : "unknown";
              String title = query.getTitle().orElse(queryId);
              String description = query.getDescription().orElse("");

              // Parameter-Schema
              Map<String, io.swagger.v3.oas.models.media.Schema<?>> parameterProperties =
                  query.getParameters().entrySet().stream()
                      .collect(
                          Collectors.toMap(
                              Entry::getKey,
                              entry -> {
                                Object schema = entry.getValue();
                                io.swagger.v3.oas.models.media.Schema<?> swaggerSchema;
                                if (schema instanceof JsonSchemaString s) {
                                  swaggerSchema =
                                      new StringSchema()
                                          .title(s.getTitle().orElse(null))
                                          .description(s.getDescription().orElse(null))
                                          .pattern(s.getPattern().orElse(null));
                                  if (s.getDefault_().isPresent()) {
                                    swaggerSchema.setDefault(s.getDefault_().get());
                                  }
                                } else if (schema instanceof JsonSchemaInteger i) {
                                  swaggerSchema =
                                      new io.swagger.v3.oas.models.media.IntegerSchema()
                                          .title(i.getTitle().orElse(null))
                                          .description(i.getDescription().orElse(null));
                                  if (i.getDefault_().isPresent()) {
                                    swaggerSchema.setDefault(i.getDefault_().get());
                                  }
                                } else {
                                  swaggerSchema = new ObjectSchema();
                                }
                                return swaggerSchema;
                              }));

              ObjectSchema parametersSchema = new ObjectSchema();
              parametersSchema.setProperties((Map) parameterProperties);

              ObjectSchema inputSchema = new ObjectSchema();
              inputSchema.setProperties(Map.of("parameters", parametersSchema));

              return new ImmutableMcpTool.Builder()
                  .id(STORED_QUERY_PREFIX + queryId)
                  .name("Stored Query - " + title)
                  .description(description)
                  .inputSchema(inputSchema)
                  .build();
            })
        .collect(Collectors.toList());
  }

  public static CollectionsResult filterAndCreateCollections(
      McpConfiguration mcpConfiguration,
      OgcApiDataV2 apiData,
      ExtensionRegistry extensionRegistry) {

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
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // Only collect queryable parameters for the included collections
    List<QueryParameterTemplateQueryable> filteredItems =
        definitions.stream()
            .flatMap(def -> def.getResources().values().stream())
            .filter(res -> collectionMap.containsKey(res.getCollectionId(apiData).orElse("")))
            .flatMap(res -> res.getOperations().values().stream())
            .flatMap(op -> op.getQueryParameters().stream())
            .filter(param -> param instanceof QueryParameterTemplateQueryable)
            .map(param -> (QueryParameterTemplateQueryable) param)
            .toList();

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

    Map<String, Map<String, Schema<?>>> collectionProperties =
        filteredItems.stream()
            .collect(
                Collectors.groupingBy(
                    QueryParameterTemplateQueryable::getCollectionId,
                    Collectors.toMap(
                        QueryParameterTemplateQueryable::getName,
                        qp -> {
                          Schema<?> schema = new ObjectSchema();
                          schema.setDescription(qp.getDescription());
                          return schema;
                        })));

    Map<String, ObjectSchema> collections =
        collectionProperties.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> {
                      Map<String, Schema<?>> props = new HashMap<>(e.getValue());
                      props.putAll(globalParameters);
                      return (ObjectSchema)
                          new ObjectSchema().properties((Map<String, Schema>) (Map<?, ?>) props);
                    }));

    return new CollectionsResult(collections, filteredItems);
  }
}
