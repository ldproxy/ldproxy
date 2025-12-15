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
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiResponse;
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
import io.swagger.v3.oas.models.media.StringSchema;
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

  private static final String COLLECTION_QUERY_PREFIX = "collection_";
  private static final String STORED_QUERY_PREFIX = "query_";

  public static List filterAndCreateStoredQueries(
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

    return queryParametersByQuery.entrySet().stream()
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

              ObjectSchema outputSchema =
                  resourceMapByQueryId.get(queryId).getOperations().values().stream()
                      .filter(op -> op.getOperationId().contains("executeStoredQuery"))
                      .findFirst()
                      .map(
                          op -> {
                            var successOpt = op.getSuccess();
                            if (successOpt.isEmpty()) {
                              throw new IllegalStateException(
                                  "No success response for stored query " + queryId);
                            }

                            Map<String, ApiMediaTypeContent> contentMap =
                                successOpt.get().getContent().entrySet().stream()
                                    .collect(
                                        Collectors.toMap(
                                            e -> e.getKey().toString(), Map.Entry::getValue));

                            Schema<?> schema = null;

                            if (contentMap.containsKey("application/geo+json")) {
                              schema = contentMap.get("application/geo+json").getSchema();
                            } else if (contentMap.containsKey("application/json")) {
                              schema = contentMap.get("application/json").getSchema();
                            } else {
                              schema = new ObjectSchema().addProperty("value", new StringSchema());
                            }

                            if (schema instanceof ObjectSchema os) {
                              return os;
                            }

                            ObjectSchema wrapper = new ObjectSchema();
                            wrapper.addProperty("value", schema);
                            return wrapper;
                          })
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Output schema missing for queryId " + queryId));

              if (inputSchema.getProperties() == null || inputSchema.getProperties().isEmpty()) {
                throw new IllegalStateException(
                    "Cannot create McpTool for queryId " + queryId + ": inputSchema is missing");
              }

              return new ImmutableMcpTool.Builder()
                  .id(STORED_QUERY_PREFIX + queryId)
                  .name("Stored Query - " + queryId)
                  .description(
                      filteredQueries.stream()
                          .filter(q -> q.getId().equals(queryId))
                          .findFirst()
                          .flatMap(StoredQueryExpression::getDescription)
                          .orElse("Tool for stored query: " + queryId))
                  .inputSchema(inputSchema)
                  .outputSchema(outputSchema)
                  .queryParameters(parameters)
                  .build();
            })
        .toList();
  }

  public static List filterAndCreateCollections(
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

    return collectionMap.entrySet().stream()
        .map(
            e -> {
              String collectionId = e.getKey();
              List<OgcApiQueryParameter> collectionQueryParameters =
                  queryParametersByCollection.getOrDefault(collectionId, List.of());
              ObjectSchema inputSchema = collections.get(collectionId);

              Schema<?> outputSchema = null;
              ApiOperation getOp = e.getValue().getOperations().get(HttpMethod.GET);

              Map<String, ApiMediaTypeContent> contentMap =
                  getOp
                      .getSuccess()
                      .map(ApiResponse::getContent)
                      .map(
                          original ->
                              original.entrySet().stream()
                                  .collect(
                                      Collectors.toMap(
                                          f ->
                                              f.getKey()
                                                  .toString(), // MediaType → "application/geo+json"
                                          Map.Entry::getValue)))
                      .orElse(Map.of());

              if (contentMap.containsKey("application/geo+json")) {
                outputSchema = contentMap.get("application/geo+json").getSchema();
              } else if (contentMap.containsKey("application/json")) {
                outputSchema = contentMap.get("application/json").getSchema();
              } else {
                outputSchema = new ObjectSchema().addProperty("value", new StringSchema());
              }

              // Standard-Description
              String description =
                  apiData.getCollections().get(collectionId).getDescription().orElse("");

              // Spezielle Anpassung für collection_unfaelle2
              if ("unfaelle2".equals(collectionId)) {
                description +=
                    " Im Gegensatz zu collection_unfaelle werden hier auch nähere Angaben zu den Straßennummern/namen gemacht.";
              }

              ObjectSchema outputObjectSchema;
              if (outputSchema instanceof ObjectSchema os) {
                outputObjectSchema = os;
              } else {
                outputObjectSchema = new ObjectSchema();
                outputObjectSchema.addProperty("value", outputSchema);
              }

              return new ImmutableMcpTool.Builder()
                  .id(COLLECTION_QUERY_PREFIX + collectionId)
                  .name(
                      "Collection Query - " + apiData.getCollections().get(collectionId).getLabel())
                  .description(description)
                  .inputSchema(inputSchema)
                  .outputSchema(outputObjectSchema)
                  .queryParameters(collectionQueryParameters)
                  .build();
            })
        .toList();
  }
}
