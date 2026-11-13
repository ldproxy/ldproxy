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
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.OgcApiResource;
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpConfiguration.McpIncludeExclude;
import de.ii.ogcapi.mcp.domain.McpTool;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class McpToolUtils {

  private static String extractQueryId(String path) {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private static final String COLLECTION_QUERY_PREFIX = "collection_";
  private static final String STORED_QUERY_PREFIX = "query_";

  public static List<McpTool> filterAndCreateStoredQueries(
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

    if (includedQueries == null && excludedQueries.isEmpty()) {
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
                      inputSchema.addProperty(
                          param.getName(), withPagingHint(param, schema, schema.getDescription()));
                    }
                  });

              /* This has been commented out for now, as there are issues with the output schema
                 that need to be addressed.

                 The output schema is taken from the OpenAPI definition, but this schema is
                 not synchronized with the schema that is generated by the recent module
                 ogcapi-collections-schema-validation. Furthermore, the schema has references
                 to schema components that do not resolve, which leads to tool errors.
                 In addition, the OpenAPI 3.0 schema is not in the JSON Schema 2020-12 version
                 that MCP expects.

                 The OpenAPI schema should also be based on the schema derived in the
                 ogcapi-collections-schema-validation module. A good time for this could be
                 the support for OpenAPI 3.1/3.2 as this version supports JSON Schema 2020-12.

                 In addition, any references need to be resolved to include a working schema in
                 the MCP tool definition.

                 Also note that /* Similar considerations apply for the output schema for a stored query as they
                 apply for a collection. The code has been commented out for now as a result.

                 Also: The schema for a stored query is a bit more complex to derive as it requires
                 an analysis of the query definition to identify which collections are involved in
                 sub-queries, and which projection clauses are applied for each sub-query.

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
               */
              ObjectSchema outputSchema = new ObjectSchema();

              if (inputSchema.getProperties() == null || inputSchema.getProperties().isEmpty()) {
                throw new IllegalStateException(
                    "Cannot create McpTool for queryId " + queryId + ": inputSchema is missing");
              }

              return (McpTool)
                  new ImmutableMcpTool.Builder()
                      .id(STORED_QUERY_PREFIX + queryId)
                      .name("Stored Query - " + queryId)
                      // what the tool is for first, clients may only show the beginning
                      .description(
                          filteredQueries.stream()
                                  .filter(q -> q.getId().equals(queryId))
                                  .findFirst()
                                  .flatMap(StoredQueryExpression::getDescription)
                                  .orElse("Tool for stored query: " + queryId)
                              + apiDescription(apiData, mcpConfiguration)
                                  .map(desc -> System.lineSeparator() + desc)
                                  .orElse(""))
                      .inputSchema(inputSchema)
                      .outputSchema(outputSchema)
                      .queryParameters(parameters)
                      .operation(operation(resourceMapByQueryId.get(queryId), "executeStoredQuery"))
                      .build();
            })
        .toList();
  }

  public static List<McpTool> filterAndCreateCollections(
      McpConfiguration mcpConfiguration,
      OgcApi api,
      ExtensionRegistry extensionRegistry,
      List<String> allowedNames) {

    OgcApiDataV2 apiData = api.getData();
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
                            .filter(param -> !param.isInternal())
                            .filter(
                                param ->
                                    allowedNames == null
                                        || allowedNames.contains(param.getName())
                                        || param instanceof QueryParameterTemplateQueryable)
                            .collect(Collectors.toList())));

    return collectionMap.entrySet().stream()
        .map(
            e -> {
              String collectionId = e.getKey();
              List<String> filterOnly =
                  apiData
                      .getExtension(McpConfiguration.class, collectionId)
                      .map(McpConfiguration::getFilterOnlyQueryables)
                      .orElse(mcpConfiguration.getFilterOnlyQueryables());
              // the queryables that are only available in filter are not arguments in the tool
              // definition, but still accepted in a call: clients that take the API description
              // literally pass any queryable as an argument
              List<OgcApiQueryParameter> collectionQueryParameters =
                  queryParametersByCollection.getOrDefault(collectionId, List.of());
              List<OgcApiQueryParameter> arguments =
                  collectionQueryParameters.stream()
                      .filter(
                          param ->
                              !(param instanceof QueryParameterTemplateQueryable)
                                  || !filterOnly.contains(param.getName()))
                      .toList();
              List<String> filterOnlyOfCollection =
                  queryParametersByCollection.getOrDefault(collectionId, List.of()).stream()
                      .filter(param -> param instanceof QueryParameterTemplateQueryable)
                      .map(OgcApiQueryParameter::getName)
                      .filter(filterOnly::contains)
                      .toList();
              ObjectSchema inputSchema = new ObjectSchema();
              for (OgcApiQueryParameter param : arguments) {
                Schema<?> schema = param.getSchema(apiData, collectionId);
                if (schema != null) {
                  inputSchema.addProperty(
                      param.getName(), argumentSchema(param, schema, filterOnlyOfCollection));
                }
              }
              boolean wildcards =
                  arguments.stream()
                      .anyMatch(
                          param ->
                              param instanceof QueryParameterTemplateQueryable queryable
                                  && QueryParameterTemplateQueryable.supportsWildcard(
                                      queryable.getType(), queryable.getValueType()));

              /* See the filterAndCreateStoredQueries() method for a discussion why the derivation
                 of the output schema has been commented out for now.

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

              ObjectSchema outputObjectSchema;
              if (outputSchema instanceof ObjectSchema os) {
                outputObjectSchema = os;
              } else {
                outputObjectSchema = new ObjectSchema();
                outputObjectSchema.addProperty("value", outputSchema);
              }
               */
              ObjectSchema outputObjectSchema = new ObjectSchema();

              // standard description, what the tool is for first, clients may only show the
              // beginning
              String description =
                  apiData
                          .getCollections()
                          .get(collectionId)
                          .getDescription()
                          .map(s -> s + System.lineSeparator())
                          .orElse("")
                      + (wildcards ? WILDCARD_HINT + System.lineSeparator() : "")
                      + apiDescription(apiData, mcpConfiguration)
                          .map(desc -> desc + System.lineSeparator())
                          .orElse("")
                      + mcpConfiguration
                          .getAddMetadataAsText()
                          .filter(flag -> flag)
                          .map(
                              flag ->
                                  "Metadata about the data in this collection: "
                                      + api.getSpatialExtent(collectionId)
                                          .map(
                                              bbox ->
                                                  "Spatial extent: Longitude between "
                                                      + bbox.getXmin()
                                                      + " and "
                                                      + bbox.getXmax()
                                                      + "; Latitude betweeen "
                                                      + bbox.getYmin()
                                                      + " and "
                                                      + bbox.getYmax()
                                                      + ". ")
                                          .orElse("")
                                      + api.getTemporalExtent(collectionId)
                                          .map(
                                              interval ->
                                                  "Temporal extent: "
                                                      + interval.humanReadable(Locale.US)
                                                      + ". ")
                                          .orElse("")
                                      + api.getItemCount(collectionId)
                                          .map(count -> "Number of objects: " + count + ". ")
                                          .orElse("")
                                      + System.lineSeparator())
                          .orElse("");

              return (McpTool)
                  new ImmutableMcpTool.Builder()
                      .id(COLLECTION_QUERY_PREFIX + collectionId)
                      .name(
                          "Collection Query - "
                              + apiData.getCollections().get(collectionId).getLabel())
                      .description(description)
                      .inputSchema(inputSchema)
                      .outputSchema(outputObjectSchema)
                      .queryParameters(collectionQueryParameters)
                      .operation(operation(e.getValue(), "getItems"))
                      .build();
            })
        .toList();
  }

  // the operation that a tool executes, the GET operation if the resource has several
  static Optional<ApiOperation> operation(OgcApiResource resource, String operationId) {
    return resource.getOperations().entrySet().stream()
        .filter(entry -> entry.getValue().getOperationId().contains(operationId))
        .sorted(Comparator.comparing(entry -> !"GET".equals(entry.getKey())))
        .map(Entry::getValue)
        .findFirst();
  }

  // the description of the API is the background of all tools and always part of the instructions
  // of the server (see McpServerImpl); clients that pass the instructions to the model do not need
  // it again in each tool description
  static Optional<String> apiDescription(OgcApiDataV2 apiData, McpConfiguration mcpConfiguration) {
    return mcpConfiguration.getApiDescriptionInTools().orElse(true)
        ? apiData.getDescription()
        : Optional.empty();
  }

  // the result of a tool call states the offset of the next page, if there is one (see
  // McpServerImpl); the schema of the parameter is copied, so the API definition is not changed
  static Schema<?> withPagingHint(
      OgcApiQueryParameter param, Schema<?> schema, String description) {
    if (!"offset".equals(param.getName())) {
      return schema;
    }
    Schema<?> copy = Json.mapper().convertValue(schema, Schema.class);
    copy.setDescription(
        (Objects.nonNull(description) && !description.isBlank() ? description + " " : "")
            + PAGING_HINT);
    return copy;
  }

  static final String PAGING_HINT =
      "If the result contains 'nextOffset', more results are available: call the tool again with"
          + " the same arguments and this value as offset.";

  // the note on wildcards is stated once in the tool description, not in every argument
  static final String WILDCARD_HINT =
      "In the arguments that filter by a property with a string value, the value may contain '*' as"
          + " a wildcard for any sequence of characters; constraints of the property, for example"
          + " 'pattern' or 'enum', are not applied to such a value.";

  // shorter descriptions of the query parameters of the features than in the API definition; the
  // tool definitions are part of the context of every request of a client
  private static final Map<String, String> ARGUMENT_DESCRIPTIONS =
      Map.of(
          "bbox",
          "Only objects whose geometry intersects the bounding box: minimum longitude, minimum"
              + " latitude, maximum longitude, maximum latitude in WGS 84 (CRS84).",
          "datetime",
          "Only objects with a temporal property that intersects the value: a date (2018-02-12), a"
              + " date-time (RFC 3339, 2018-02-12T23:20:50Z) or 'now', or an interval of two such"
              + " values separated by '/', with '..' for an open end (2018-02-12/..).",
          "ids",
          "Only the objects with one of these ids.",
          "limit",
          "The maximum number of objects in the result.",
          "sortby",
          "The properties to sort by, each with an optional prefix '+' (ascending, the default) or"
              + " '-' (descending).",
          "properties",
          "Only these properties are returned for each object; the id is always returned. Not"
              + " together with exclude-properties.",
          "exclude-properties",
          "These properties are not returned for each object. Without this argument, the"
              + " properties in its default value are left out; an empty list returns all"
              + " properties. Not together with properties.",
          "filter",
          "A filter expression in CQL2 text over the queryables of the collection, for example"
              + " name = 'A' AND height > 10. Supported are comparisons, LIKE, BETWEEN, IN, IS NULL,"
              + " AND, OR, NOT and the spatial (S_INTERSECTS, coordinates in WGS 84), temporal"
              + " (T_INTERSECTS) and array (A_CONTAINS) functions.");

  /**
   * The schema of an argument of a collection tool: a copy of the schema of the query parameter, so
   * that the API definition is not changed, with the description of the argument. The pattern of a
   * datetime value is left out, the value is still checked when the parameter is parsed.
   */
  static Schema<?> argumentSchema(
      OgcApiQueryParameter param, Schema<?> schema, List<String> filterOnlyQueryables) {
    Schema<?> copy = Json.mapper().convertValue(schema, Schema.class);
    String description = param.getDescription();
    if (param instanceof QueryParameterTemplateQueryable queryable) {
      description = queryable.getBaseDescription();
    } else if (ARGUMENT_DESCRIPTIONS.containsKey(param.getName())) {
      description = ARGUMENT_DESCRIPTIONS.get(param.getName());
      if ("datetime".equals(param.getName())) {
        copy.setPattern(null);
      }
      if ("filter".equals(param.getName()) && !filterOnlyQueryables.isEmpty()) {
        description +=
            " Queryables that are only available here: "
                + String.join(", ", filterOnlyQueryables)
                + ".";
      }
    }
    copy.setDescription(description);
    return withPagingHint(param, copy, description);
  }
}
