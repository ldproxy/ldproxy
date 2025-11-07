/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * @buildingBlock MCP
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: MCP
 *   enabled: true
 * ```
 * </code>
 */
@Value.Immutable
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "MCP")
@JsonDeserialize(builder = ImmutableMcpConfiguration.Builder.class)
public interface McpConfiguration extends ExtensionConfiguration {

  /**
   * @langEn TODO
   * @langDe TODO
   * @default []
   * @since v4.6
   */
  Optional<McpIncludeExclude> getIncluded();

  /**
   * @langEn TODO
   * @langDe TODO
   * @default []
   * @since v4.6
   */
  Optional<McpIncludeExclude> getExcluded();

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableMcpIncludeExclude.Builder.class)
  interface McpIncludeExclude {

    /**
     * @langEn TODO
     * @langDe TODO
     * @default []
     * @since v4.6
     */
    List<String> getCollections();

    /**
     * @langEn TODO
     * @langDe TODO
     * @default []
     * @since v4.6
     */
    List<String> getQueries();
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableMcpConfiguration.Builder();
  }
}
