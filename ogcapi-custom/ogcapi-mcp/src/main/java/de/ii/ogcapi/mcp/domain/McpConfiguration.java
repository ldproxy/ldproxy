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
import java.util.stream.Stream;
import org.immutables.value.Value;

/**
 * @buildingBlock MCP
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: MCP
 *   enabled: true
 *   included:
 *     collections:
 *       - "*"
 *     queries:
 *       - "*"
 *     processes:
 *       - "*"
 *   excluded:
 *     collections:
 *      - "collection_to_exclude"
 * ```
 * </code>
 */
@Value.Immutable
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "MCP")
@JsonDeserialize(builder = ImmutableMcpConfiguration.Builder.class)
public interface McpConfiguration extends ExtensionConfiguration {

  /**
   * @langEn Specifies which collections, stored queries and processes should be included in the MCP
   *     schema.
   * @langDe Gibt an, welche Collections, Stored Queries und Prozesse ins MCP-Schema übernommen
   *     werden sollen.
   * @default []
   * @since v4.6
   */
  Optional<McpIncludeExclude> getIncluded();

  /**
   * @langEn Specifies which collections, stored queries and processes should be excluded from the
   *     MCP schema.
   * @langDe Gibt an, welche Collections, Stored Queries und Prozesse vom MCP-Schema ausgeschlossen
   *     werden sollen.
   * @default []
   * @since v4.6
   */
  Optional<McpIncludeExclude> getExcluded();

  /**
   * @langEn If set to `true`, the collection metadata (spatial and temporal extent, item count) is
   *     included in the textual description.
   * @langDe Wenn auf `true` gesetzt, werden die Metadaten der Sammlung (räumlicher und zeitlicher
   *     Umfang, Anzahl der Objekte) in die Textbeschreibung aufgenommen.
   * @default false
   * @since v4.7
   */
  Optional<Boolean> getAddMetadataAsText();

  /**
   * @langEn If set to `false`, the description of the API is only given in the instructions of the
   *     MCP server, not also at the end of each tool description. This keeps the tool definitions
   *     small, but clients that do not pass the instructions to the model then no longer see the
   *     description of the API.
   * @langDe Wenn auf `false` gesetzt, wird die Beschreibung der API nur in den Anweisungen
   *     (Instructions) des MCP-Servers angegeben, nicht zusätzlich am Ende jeder Tool-Beschreibung.
   *     Das hält die Tool-Definitionen klein, Clients, die die Anweisungen nicht an das Modell
   *     weitergeben, sehen die Beschreibung der API dann aber nicht mehr.
   * @default true
   * @since v5.0
   */
  Optional<Boolean> getApiDescriptionInTools();

  /**
   * @langEn Queryables that are not arguments of their own in the tools of the collections. They
   *     can still be used in the argument `filter`. This keeps the tool definitions small, for
   *     example for queryables that are rarely used on their own.
   * @langDe Queryables, die in den Tools der Collections keine eigenen Argumente sind. Sie können
   *     weiterhin im Argument `filter` verwendet werden. Das hält die Tool-Definitionen klein, zum
   *     Beispiel bei Queryables, die selten allein verwendet werden.
   * @default []
   * @since v5.0
   */
  List<String> getFilterOnlyQueryables();

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableMcpConfiguration.Builder()
        .from(source)
        .from(this)
        .filterOnlyQueryables(
            Stream.concat(
                    ((McpConfiguration) source).getFilterOnlyQueryables().stream(),
                    getFilterOnlyQueryables().stream())
                .distinct()
                .toList())
        .build();
  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableMcpIncludeExclude.Builder.class)
  interface McpIncludeExclude {

    /**
     * @langEn List of collection Ids to be included or excluded.
     * @langDe Liste der Collection-Ids, die eingeschlossen oder ausgeschlossen werden sollen.
     * @default []
     * @since v4.6
     */
    List<String> getCollections();

    /**
     * @langEn List of stored query Ids to be included or excluded.
     * @langDe Liste der Stored-Query-Ids, die eingeschlossen oder ausgeschlossen werden sollen.
     * @default []
     * @since v4.6
     */
    List<String> getQueries();

    /**
     * @langEn List of process ids or titles to be included or excluded, `*` includes all processes.
     *     Only processes that can be executed synchronously are tools, and only if they are
     *     included: without an entry in `included`, no process is a tool. A tool call executes the
     *     process like a request to its execution endpoint and requires the same permissions.
     * @langDe Liste der Prozess-Ids oder -Titel, die eingeschlossen oder ausgeschlossen werden
     *     sollen, `*` schließt alle Prozesse ein. Nur Prozesse, die synchron ausgeführt werden
     *     können, sind Tools, und nur, wenn sie eingeschlossen sind: ohne einen Eintrag in
     *     `included` ist kein Prozess ein Tool. Ein Tool-Aufruf führt den Prozess wie eine Anfrage
     *     an seinen Ausführungs-Endpunkt aus und erfordert dieselben Berechtigungen.
     * @default []
     * @since v5.0
     */
    List<String> getProcesses();
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableMcpConfiguration.Builder();
  }
}
