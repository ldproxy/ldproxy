/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.domain;

import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import org.immutables.value.Value;

@Value.Immutable
public interface McpTool {

  static McpTool of(String name, String description, JsonSchemaObject inputSchema) {
    return new ImmutableMcpTool.Builder()
        .name(name)
        .description(description)
        .inputSchema(inputSchema)
        .build();
  }

  String getName();

  String getDescription();

  JsonSchemaObject getInputSchema();
}
