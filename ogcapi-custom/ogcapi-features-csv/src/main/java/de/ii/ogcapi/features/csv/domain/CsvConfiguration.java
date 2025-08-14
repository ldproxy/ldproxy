/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.csv.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.features.core.domain.SfFlatConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.Map;
import org.immutables.value.Value;

/**
 * @buildingBlock CSV
 * @examplesEn The following configuration enables CSV as a feature encoding, where all objects and
 *     arrays are flattened with an underscore as a separator and two properties per array property.
 *     <p><code>
 * ```yaml
 * - buildingBlock: CSV
 *   enabled: true
 *   transformations:
 *     '*':
 *       flatten: '_'
 *   maxMultiplicity: 2
 * ```
 *     <p>For a feature with the following "properties" member in GeoJSON:
 *     <p><code>
 * ```yaml
 * {
 *   "att1": "foo",
 *   "att2": [ "bar1", "bar2", "bar3" ]
 *   "att3": {
 *     "value": 123,
 *     "values": [ 456, 789, 0 ]
 *   }
 * }
 * ```
 *     </code>
 *     <p>The resulting CSV file would be:
 *     <p><code>
 * ```csv
 * att1,att2_1,att2_2,att3_value,att3_values_1,att3_values_2
 * foo,bar1,bar2,123,456,789
 * ```
 *     </code>
 * @examplesDe Die folgende Konfiguration aktiviert CSV als Feature-Kodierung, bei der alle Objekte und Arrays mit einem Unterstrich als Trennzeichen und zwei Eigenschaften pro Array-Eigenschaft abgeflacht werden.
 *     <p><code>
 * ```yaml
 * - buildingBlock: CSV
 *   enabled: true
 *   transformations:
 *     '*':
 *       flatten: '_'
 *   maxMultiplicity: 2
 * ```
 *     <p>Für ein Feature mit den folgenden GeoJSON-"properties":
 *     <p><code>
 * ```yaml
 * {
 *   "att1": "foo",
 *   "att2": [ "bar1", "bar2", "bar3" ]
 *   "att3": {
 *     "value": 123,
 *     "values": [ 456, 789, 0 ]
 *   }
 * }
 * ```
 * </code>
 *     <p>Die resultierende CSV-Datei würde wie folgt aussehen:
 *     <p><code>
 * ```csv
 * att1,att2_1,att2_2,att3_value,att3_values_1,att3_values_2
 * foo,bar1,bar2,123,456,789
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true, attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "CSV")
@JsonDeserialize(builder = ImmutableCsvConfiguration.Builder.class)
public interface CsvConfiguration extends SfFlatConfiguration {

  /**
   * @langEn Change the default value of the [profile parameter](features.md#query-parameters) for
   *     this feature format. The value is an object where the key is the id of a profile set, such
   *     as `rel`, and the value is the default profile for the profile set, e.g., `rel-as-key`.
   *     These defaults override the defaults specified in the [Features](features.md) building
   *     block.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](features.md#query-parameter) für
   *     Features. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `rel`, und der Wert das Standardprofil für den Profilsatz, z. B. `rel-as-key`. Diese
   *     Vorgaben haben Vorrang vor den im [Features](features.md)-Baustein angegebenen
   *     Standardprofilen.
   * @since v4.2
   * @default {}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableCsvConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return ((ImmutableCsvConfiguration.Builder) source.getBuilder())
        .from(source)
        .from(this)
        .transformations(
            SfFlatConfiguration.super
                .mergeInto((PropertyTransformations) source)
                .getTransformations())
        .defaultProfiles(
            SfFlatConfiguration.super
                .mergeInto((ProfilesConfiguration) source)
                .getDefaultProfiles())
        .build();
  }
}
