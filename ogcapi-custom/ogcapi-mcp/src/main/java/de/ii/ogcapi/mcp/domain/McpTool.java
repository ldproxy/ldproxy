/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public interface McpTool {

  static McpTool of(
      String id,
      String name,
      String description,
      io.swagger.v3.oas.models.media.ObjectSchema inputSchema,
      io.swagger.v3.oas.models.media.ObjectSchema outputSchema,
      List<OgcApiQueryParameter> queryParameters) {
    return new ImmutableMcpTool.Builder()
        .id(id)
        .name(name)
        .description(description)
        .inputSchema(inputSchema)
        .outputSchema(outputSchema)
        .queryParameters(queryParameters)
        .build();
  }

  String getId();

  String getName();

  String getDescription();

  io.swagger.v3.oas.models.media.ObjectSchema getInputSchema();

  io.swagger.v3.oas.models.media.ObjectSchema getOutputSchema();

  @JsonIgnore
  List<OgcApiQueryParameter> getQueryParameters();
}
