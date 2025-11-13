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
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject.Builder;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
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

  private static JsonSchemaObject convertToJsonSchema(Object openApiSchema, String name) {
    ImmutableJsonSchemaObject.Builder builder = new ImmutableJsonSchemaObject.Builder();

    if (openApiSchema instanceof StringSchema s) {
      builder.description(
          s.getDescription() != null ? s.getDescription() : "String parameter: " + name);
      builder.title(s.getTitle() != null ? s.getTitle() : name);
    } else if (openApiSchema instanceof io.swagger.v3.oas.models.media.ArraySchema a) {
      builder.description("Array parameter: " + name);
      builder.title(name);
    } else if (openApiSchema instanceof io.swagger.v3.oas.models.media.IntegerSchema i) {
      builder.description("Integer parameter: " + name);
      builder.title(name);
    } else {
      builder.description("Generic parameter: " + name);
      builder.title(name);
    }

    return builder.build();
  }

  public static class CollectionsResult {
    private final Map<String, JsonSchemaObject> collections;
    private final List<QueryParameterTemplateQueryable> filteredItems;

    public CollectionsResult(
        Map<String, JsonSchemaObject> collections,
        List<QueryParameterTemplateQueryable> filteredItems) {
      this.collections = collections;
      this.filteredItems = filteredItems;
    }

    public Map<String, JsonSchemaObject> getCollections() {
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
              Map<String, JsonSchemaObject> parameterProperties =
                  query.getParameters().entrySet().stream()
                      .collect(
                          Collectors.toMap(
                              Entry::getKey,
                              entry -> {
                                Object schema = entry.getValue();
                                Builder builder = new Builder();

                                String paramTitle = entry.getKey();
                                String paramDescription = "";
                                String pattern = null;
                                Object defaultValue = null;

                                if (schema instanceof JsonSchemaString s) {
                                  paramTitle =
                                      s.getTitle() != null
                                          ? String.valueOf(s.getTitle())
                                              .replace("Optional[", "")
                                              .replace("]", "")
                                          : paramTitle;
                                  paramDescription =
                                      s.getDescription() != null
                                          ? String.valueOf(s.getDescription())
                                              .replace("Optional[", "")
                                              .replace("]", "")
                                          : "";
                                  pattern = String.valueOf(s.getPattern());
                                  defaultValue = s.getDefault_();
                                } else if (schema instanceof JsonSchemaInteger i) {
                                  paramTitle =
                                      i.getTitle() != null
                                          ? String.valueOf(i.getTitle())
                                              .replace("Optional[", "")
                                              .replace("]", "")
                                          : paramTitle;
                                  paramDescription =
                                      i.getDescription() != null
                                          ? String.valueOf(i.getDescription())
                                              .replace("Optional[", "")
                                              .replace("]", "")
                                          : "";
                                  defaultValue = i.getDefault_();
                                }

                                builder.title(paramTitle).description(paramDescription);

                                if (pattern != null) builder.codelistId(pattern);
                                if (defaultValue != null) builder.default_(defaultValue);

                                return builder.build();
                              }));

              JsonSchemaObject parametersSchema =
                  new Builder().properties(parameterProperties).build();
              JsonSchemaObject inputSchema =
                  new Builder().properties(Map.of("parameters", parametersSchema)).build();

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

    // System.out.println("Definitions found: " + definitions);
    // Bbox, datetime etc. finden wir am Anfang im queryParameters Array

    Map<String, JsonSchemaObject> globalParameters = new HashMap<>();

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
            System.out.println("Global parameter found: " + name + " with schema: " + schema);
            globalParameters.put(name, convertToJsonSchema(schema, name));
          }
        }
      }
    }
    System.out.println("Global parameters: " + globalParameters);

    List<QueryParameterTemplateQueryable> allItems =
        definitions.stream()
            .flatMap(def -> def.getResources().values().stream())
            .flatMap(res -> res.getOperations().values().stream())
            .flatMap(op -> op.getQueryParameters().stream())
            .filter(param -> param instanceof QueryParameterTemplateQueryable)
            .map(param -> (QueryParameterTemplateQueryable) param)
            .toList();

    List<QueryParameterTemplateQueryable> filteredItems;

    Optional<McpIncludeExclude> includedOpt = mcpConfiguration.getIncluded();
    Optional<McpIncludeExclude> excludedOpt = mcpConfiguration.getExcluded();

    List<String> includedCollections =
        includedOpt.map(McpIncludeExclude::getCollections).orElse(null);
    List<String> excludedCollections =
        excludedOpt.map(McpIncludeExclude::getCollections).orElse(List.of());

    if (includedOpt.isEmpty() && excludedOpt.isEmpty()) {
      filteredItems = allItems;
    } else if (includedCollections == null) {
      filteredItems = List.of();
    } else if (includedCollections.contains("*")) {
      filteredItems =
          allItems.stream()
              .filter(qp -> !excludedCollections.contains(qp.getCollectionId()))
              .toList();
    } else {
      filteredItems =
          allItems.stream()
              .filter(qp -> includedCollections.contains(qp.getCollectionId()))
              .filter(qp -> !excludedCollections.contains(qp.getCollectionId()))
              .toList();
    }

    Map<String, Map<String, JsonSchemaObject>> collectionProperties =
        filteredItems.stream()
            .collect(
                Collectors.groupingBy(
                    QueryParameterTemplateQueryable::getCollectionId,
                    Collectors.toMap(
                        QueryParameterTemplateQueryable::getName,
                        qp -> new Builder().description(qp.getDescription()).build())));

    Map<String, JsonSchemaObject> collections =
        collectionProperties.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    e -> {
                      Map<String, JsonSchemaObject> props = new HashMap<>(e.getValue());
                      props.putAll(globalParameters);
                      return new Builder().properties(props).build();
                    }));

    return new CollectionsResult(collections, filteredItems);
  }
}
