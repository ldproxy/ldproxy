/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.queryables.domain.QueryParameterTemplateQueryable;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.mcp.domain.ImmutableMcpSchema;
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpSchema;
import de.ii.ogcapi.mcp.domain.McpServer;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class McpServerImpl implements McpServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpServerImpl.class);

  private final Map<String, McpSchema> schemas = new ConcurrentHashMap<>();
  private final StoredQueryRepository storedQueryRepository;

  // private final Map<String, McpSyncServer> servers = new ConcurrentHashMap<>();

  @Inject
  public McpServerImpl(StoredQueryRepository storedQueryRepository) {
    this.storedQueryRepository = storedQueryRepository;
  }

  @Override
  public McpSchema getSchema(OgcApiDataV2 apiData, List<QueryParameterTemplateQueryable> allItems) {

    if (!schemas.containsKey(apiData.getStableHash())) {
      LOGGER.debug("Creating MCP schema for API {}", apiData.getId());

      McpConfiguration mcpConfiguration =
          apiData.getExtension(McpConfiguration.class).orElseThrow();

      // TODO: generate MCP schema based on apiData and mcpConfiguration, put it in the map

      List<StoredQueryExpression> storedQueries = storedQueryRepository.getAll(apiData);

      List<ImmutableMcpTool> queryTools =
          storedQueries.stream()
              .map(
                  query -> {
                    String queryId = query.getId() != null ? query.getId() : "unknown";
                    String title =
                        query.getTitle() != null ? String.valueOf(query.getTitle()) : queryId;
                    String description =
                        query.getDescription() != null
                            ? String.valueOf(query.getDescription())
                            : "";

                    // Parameter-Schema
                    Map<String, JsonSchemaObject> parameterProperties =
                        query.getParameters().entrySet().stream()
                            .collect(
                                Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> {
                                      Object schema = entry.getValue();
                                      ImmutableJsonSchemaObject.Builder builder =
                                          new ImmutableJsonSchemaObject.Builder();

                                      String paramTitle = entry.getKey();
                                      String paramDescription = "";
                                      String pattern = null;
                                      Object defaultValue = null;

                                      if (schema instanceof JsonSchemaString s) {
                                        paramTitle =
                                            s.getTitle() != null
                                                ? String.valueOf(s.getTitle())
                                                : paramTitle;
                                        paramDescription =
                                            s.getDescription() != null
                                                ? String.valueOf(s.getDescription())
                                                : "";
                                        pattern = String.valueOf(s.getPattern());
                                        defaultValue = s.getDefault_();
                                      } else if (schema instanceof JsonSchemaInteger i) {
                                        paramTitle =
                                            i.getTitle() != null
                                                ? String.valueOf(i.getTitle())
                                                : paramTitle;
                                        paramDescription =
                                            i.getDescription() != null
                                                ? String.valueOf(i.getDescription())
                                                : "";
                                        defaultValue = i.getDefault_();
                                      }

                                      builder.title(paramTitle).description(paramDescription);

                                      if (pattern != null) builder.codelistId(pattern);
                                      if (defaultValue != null) builder.default_(defaultValue);

                                      return builder.build();
                                    }));

                    JsonSchemaObject parametersSchema =
                        new ImmutableJsonSchemaObject.Builder()
                            .properties(parameterProperties)
                            .build();

                    JsonSchemaObject inputSchema =
                        new ImmutableJsonSchemaObject.Builder()
                            .properties(Map.of("parameters", parametersSchema))
                            .build();

                    return new ImmutableMcpTool.Builder()
                        .name(title)
                        .description(description)
                        .inputSchema(inputSchema)
                        .build();
                  })
              .collect(Collectors.toList());

      // Collections
      Map<String, Map<String, JsonSchemaObject>> collectionProperties =
          allItems.stream()
              .collect(
                  Collectors.groupingBy(
                      QueryParameterTemplateQueryable::getCollectionId,
                      Collectors.toMap(
                          QueryParameterTemplateQueryable::getName,
                          qp ->
                              new ImmutableJsonSchemaObject.Builder()
                                  .description(qp.getDescription())
                                  .build())));

      Map<String, JsonSchemaObject> properties =
          collectionProperties.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, // collectionId
                      e ->
                          new ImmutableJsonSchemaObject.Builder()
                              .properties(e.getValue())
                              .build()));

      schemas.put(
          apiData.getStableHash(),
          new ImmutableMcpSchema.Builder()
              .addAllTools(
                  properties.entrySet().stream()
                      .map(
                          e ->
                              new ImmutableMcpTool.Builder()
                                  .name(e.getKey())
                                  .description(
                                      String.valueOf(
                                          apiData
                                              .getCollections()
                                              .get(e.getKey())
                                              .getDescription()))
                                  .inputSchema(e.getValue())
                                  .build())
                      .toList())
              .addAllTools(queryTools)
              .build());
    }
    return schemas.get(apiData.getStableHash());
  }
}
