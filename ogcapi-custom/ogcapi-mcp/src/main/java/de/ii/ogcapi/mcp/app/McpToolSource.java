/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpTool;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A kind of resource of an API that is published as MCP tools, for example the features of the
 * collections, the stored queries or the processes. The ids of the tools of a source start with its
 * prefix.
 */
interface McpToolSource {

  String getPrefix();

  List<McpTool> getTools(OgcApi api, McpConfiguration configuration);

  /**
   * Changes when the tools of the source change without a change of the API, for example when the
   * stored queries are reloaded; the tools are cached until the API or a fingerprint changes.
   */
  default int getFingerprint(OgcApi api) {
    return 0;
  }

  /**
   * Executes a tool of this source.
   *
   * @param mcpRequest the context of the MCP request, if available, from which internal requests
   *     are derived and which carries the user
   * @return the result as a JSON object, sent as text and as structured content
   */
  Map<String, Object> call(
      OgcApi api,
      McpTool tool,
      Map<String, Object> arguments,
      Optional<ApiRequestContext> mcpRequest)
      throws IOException;
}
