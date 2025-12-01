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
 * @buildingBlock MCP * @langEn ### Purpose *
 *     <p>The MCP building block enables the creation of an MCP schema, which allows ldproxy to be
 *     used with Large Language Models (LLMs). *
 *     <p>This configuration controls which collections and queries are included or excluded in the
 *     MCP schema. * @langDe ### Zweck *
 *     <p>Der MCP-BuildingBlock dient dazu, ein MCP-Schema zu erstellen, sodass ldproxy mit Large
 *     Language Models (LLMs) genutzt werden kann. *
 *     <p>Über diese Konfiguration wird gesteuert, welche Collections und Queries im MCP-Schema
 *     enthalten oder ausgeschlossen sind.
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: MCP
 *   enabled: true
 *      included:
 *      collections:
 *        - "*"
 *      queries:
 *        - "*"
 *    excluded:
 *      collections:
 *        - "collection_to_exclude"
 * ```
 * </code>
 */
@Value.Immutable
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "MCP")
@JsonDeserialize(builder = ImmutableMcpConfiguration.Builder.class)
public interface McpConfiguration extends ExtensionConfiguration {

  /**
   * @langEn Specifies which collections and queries should be included in the MCP schema.
   * @langDe Gibt an, welche Collections und Queries ins MCP-Schema übernommen werden sollen.
   * @default []
   * @since v4.6
   */
  Optional<McpIncludeExclude> getIncluded();

  /**
   * @langEn Specifies which collections and queries should be excluded from the MCP schema.
   * @langDe Gibt an, welche Collections und Queries vom MCP-Schema ausgeschlossen werden sollen.
   * @default []
   * @since v4.6
   */
  Optional<McpIncludeExclude> getExcluded();

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableMcpIncludeExclude.Builder.class)
  interface McpIncludeExclude {

    /**
     * @langEn List of collection IDs to be included or excluded.
     * @langDe Liste der Collection-IDs, die eingeschlossen oder ausgeschlossen werden sollen.
     * @default []
     * @since v4.6
     */
    List<String> getCollections();

    /**
     * @langEn List of query IDs to be included or excluded.
     * @langDe Liste der Query-IDs, die eingeschlossen oder ausgeschlossen werden sollen.
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
