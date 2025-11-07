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
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.mcp.domain.ImmutableMcpSchema;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpSchema;
import de.ii.ogcapi.mcp.domain.McpServer;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
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

  // private final Map<String, McpSyncServer> servers = new ConcurrentHashMap<>();

  @Inject
  public McpServerImpl() {}

  @Override
  public McpSchema getSchema(OgcApiDataV2 apiData, List<QueryParameterTemplateQueryable> allItems) {

    if (!schemas.containsKey(apiData.getStableHash())) {
      LOGGER.debug("Creating MCP schema for API {}", apiData.getId());

      McpConfiguration mcpConfiguration =
          apiData.getExtension(McpConfiguration.class).orElseThrow();

      // TODO: generate MCP schema based on apiData and mcpConfiguration, put it in the map

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
                              new de.ii.ogcapi.mcp.domain.ImmutableMcpTool.Builder()
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
              .build());
    }

    return schemas.get(apiData.getStableHash());
  }
}
