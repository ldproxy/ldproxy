/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.projections.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.projections.app.ImmutableProjectionsConfiguration.Builder;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.List;
import java.util.stream.Stream;
import org.immutables.value.Value;

/**
 * @buildingBlock PROJECTIONS
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: PROJECTIONS
 *   enabled: true
 *   defaultExcludedProperties:
 *   - lastUpdated
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PROJECTIONS")
@JsonDeserialize(builder = Builder.class)
public interface ProjectionsConfiguration extends ExtensionConfiguration {

  abstract class Builder extends ExtensionConfiguration.Builder {}

  /**
   * @langEn The properties that are left out of the features by default, that is, if a request sets
   *     neither `properties` nor `exclude-properties`. If no properties are set, the properties
   *     with the role `AUXILIARY` in the feature schema are left out by default. A request can ask
   *     for all properties with an empty value of `exclude-properties`, or for specific properties
   *     with `properties`. Entries at the API level apply to every collection; entries on a
   *     collection are merged with the API-level list (duplicates de-duplicated).
   * @langDe Die Eigenschaften, die standardmäßig nicht in den Features ausgegeben werden, d.h. wenn
   *     eine Anfrage weder `properties` noch `exclude-properties` angibt. Sind keine Eigenschaften
   *     angegeben, werden standardmäßig die Eigenschaften mit der Rolle `AUXILIARY` im
   *     Feature-Schema weggelassen. Eine Anfrage kann mit einem leeren Wert von
   *     `exclude-properties` alle Eigenschaften anfordern oder mit `properties` bestimmte
   *     Eigenschaften. Einträge auf API-Ebene gelten für jede Collection; Einträge an einer
   *     Collection werden mit der Liste auf API-Ebene zusammengeführt (Duplikate werden entfernt).
   * @default []
   * @since v4.9
   */
  List<String> getDefaultExcludedProperties();

  @Override
  default Builder getBuilder() {
    return new ImmutableProjectionsConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    ProjectionsConfiguration src = (ProjectionsConfiguration) source;
    return new ImmutableProjectionsConfiguration.Builder()
        .from(source)
        .from(this)
        .defaultExcludedProperties(
            Stream.concat(
                    src.getDefaultExcludedProperties().stream(),
                    getDefaultExcludedProperties().stream())
                .distinct()
                .toList())
        .build();
  }
}
