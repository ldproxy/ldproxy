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
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.OgcApiResource;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpConfiguration.McpIncludeExclude;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import io.swagger.v3.oas.models.media.IntegerSchema;
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

  public static class SimpleOgcApiQueryParameter implements OgcApiQueryParameter {

    private final String name;
    private final String title;
    private final String description;
    private final String type;
    private final boolean required;

    public SimpleOgcApiQueryParameter(
        String name, String title, String description, String type, boolean required) {
      this.name = name;
      this.title = title;
      this.description = description;
      this.type = type;
      this.required = required;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean getRequired(OgcApiDataV2 apiData, Optional<String> collectionId) {
      return required;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public Schema<?> getSchema(OgcApiDataV2 apiData, Optional<String> collectionId) {
      Schema<?> s =
          switch (type) {
            case "integer" -> new IntegerSchema().title(title).description(description);
            case "string" -> new StringSchema().title(title).description(description);
            default -> new StringSchema().title(title).description(description);
          };
      return s;
    }

    @Override
    public boolean matchesPath(String definitionPath) {
      return false;
    }

    @Override
    public boolean isApplicable(OgcApiDataV2 apiData, String path, HttpMethods method) {
      return true;
    }

    @Override
    public boolean isApplicable(
        OgcApiDataV2 apiData, String path, String collectionId, HttpMethods method) {
      return true;
    }

    @Override
    public SchemaValidator getSchemaValidator() {
      return (schemaJson, valueJson) -> Optional.empty();
    }
  }

  public static StoredQueriesResult filterAndCreateStoredQueries(
      List<StoredQueryExpression> storedQueries, McpConfiguration mcpConfiguration) {

    Optional<McpIncludeExclude> includedOpt = mcpConfiguration.getIncluded();
    Optional<McpIncludeExclude> excludedOpt = mcpConfiguration.getExcluded();

    List<String> includedQueries = includedOpt.map(McpIncludeExclude::getQueries).orElse(null);
    List<String> excludedQueries = excludedOpt.map(McpIncludeExclude::getQueries).orElse(List.of());

    // -------------------------------
    // Filtering
    // -------------------------------
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

    // -------------------------------
    // Build result containers
    // -------------------------------
    Map<String, List<OgcApiQueryParameter>> paramsByQuery = new HashMap<>();
    List<ImmutableMcpTool> tools =
        filteredQueries.stream()
            .map(
                query -> {
                  String queryId = query.getId() != null ? query.getId() : "unknown";
                  String title = query.getTitle().orElse(queryId);
                  String description = query.getDescription().orElse("");

                  // Extract schema from StoredQuery parameters
                  Map<String, Schema<?>> parameterProperties =
                      query.getParameters().entrySet().stream()
                          .collect(
                              Collectors.toMap(
                                  Entry::getKey,
                                  entry -> {
                                    Object schema = entry.getValue();
                                    Schema<?> swaggerSchema;

                                    if (schema instanceof JsonSchemaString s) {
                                      swaggerSchema =
                                          new StringSchema()
                                              .title(s.getTitle().orElse(null))
                                              .description(s.getDescription().orElse(null))
                                              .pattern(s.getPattern().orElse(null));
                                      s.getDefault_().ifPresent(swaggerSchema::setDefault);
                                    } else if (schema instanceof JsonSchemaInteger i) {
                                      swaggerSchema =
                                          new io.swagger.v3.oas.models.media.IntegerSchema()
                                              .title(i.getTitle().orElse(null))
                                              .description(i.getDescription().orElse(null));
                                      i.getDefault_().ifPresent(swaggerSchema::setDefault);
                                    } else {
                                      swaggerSchema = new ObjectSchema();
                                    }
                                    return swaggerSchema;
                                  }));
                  System.out.println(
                      "Parameter Properties for " + queryId + ": " + parameterProperties);

                  // Convert to query parameters
                  List<OgcApiQueryParameter> queryParameters =
                      parameterProperties.entrySet().stream()
                          .map(
                              entry ->
                                  new SimpleOgcApiQueryParameter(
                                      entry.getKey(),
                                      entry.getValue().getTitle(),
                                      entry.getValue().getDescription(),
                                      entry.getValue().getType(),
                                      false))
                          .collect(Collectors.toList());

                  paramsByQuery.put(STORED_QUERY_PREFIX + queryId, queryParameters);
                  System.out.println(
                      "Stored Query Parameters for " + queryId + ": " + queryParameters);
                  System.out.println("paramsByQuery" + paramsByQuery);

                  // Input schema for the tool
                  ObjectSchema inputSchema = new ObjectSchema();
                  inputSchema.setProperties((Map) parameterProperties);

                  return new ImmutableMcpTool.Builder()
                      .id(STORED_QUERY_PREFIX + queryId)
                      .name("Stored Query - " + title)
                      .description(description)
                      .inputSchema(inputSchema)
                      .queryParameters(queryParameters)
                      .build();
                })
            .collect(Collectors.toList());

    return new StoredQueriesResult(tools, paramsByQuery);
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
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

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

    System.out.println("queryParametersByCollection" + queryParametersByCollection);

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
                    Map.Entry::getKey,
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

    for (Map.Entry<String, List<OgcApiQueryParameter>> entry :
        queryParametersByCollection.entrySet()) {
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

    return new CollectionsResult(collections, queryParametersByCollection);
  }
}
