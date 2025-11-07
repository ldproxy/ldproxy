/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.domain;

import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public interface McpSchema {

  static McpSchema of(List<McpTool> tools) {
    return new ImmutableMcpSchema.Builder().tools(tools).build();
  }

  List<McpTool> getTools();
}
