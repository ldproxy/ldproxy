/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.features.core.domain.FeatureFormatConfiguration;
import de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock JSON_FG
 * @examplesEn Example of the information in the configuration file for the entire API (from the API
 *     for [Topographic Data in Daraa, Syria](https://demo.ldproxy.net/daraa)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: JSON_FG
 *   enabled: true
 *   featureType:
 *   - nas:{{type}}
 * ```
 *     </code>
 *     <p>Additional information per feature collection with an attribute `F_CODE` (for which `role:
 *     TYPE` was set in the provider configuration) to set the object type:
 *     <p><code>
 * ```yaml
 * - buildingBlock: JSON_FG
 *   featureType:
 *   - nas:{{type}}
 * ```
 *     </code>
 *     <p>This outputs the object type as follows for a value of "GB075" in the 'F_CODE' attribut:
 *     <p><code>
 * ```json
 * {
 *   "type": "Feature",
 *   "id": 1,
 *   "featureType": "nas:GB075",
 *   ...
 * }
 * ```
 *     </code>
 * @examplesDe Beispiel für die Angaben in der Konfigurationsdatei für die gesamte API (aus der API
 *     für [Topographische Daten in Daraa, Syrien](https://demo.ldproxy.net/daraa)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: JSON_FG
 *   enabled: true
 *   featureType:
 *   - nas:{{type}}
 * ```
 *     </code>
 *     <p>Ergänzende Angaben pro Feature Collection mit einem Attribut `F_CODE` (für das in der
 *     Provider-Konfiguration `role: TYPE` gesetzt wurde), um die Objektart zu setzen:
 *     <p><code>
 * ```yaml
 * - buildingBlock: JSON_FG
 *   featureType:
 *   - nas:{{type}}
 * ```
 *     </code>
 *     <p>Hierdurch wird bei einem Wert von "GB075" im Attribut `F_CODE` die Objektart wie folgt
 *     ausgegeben:
 *     <p><code>
 * ```json
 * {
 *   "type": "Feature",
 *   "id": 1,
 *   "featureType": "nas:GB075",
 *   ...
 * }
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "JSON_FG")
@JsonDeserialize(builder = Builder.class)
public interface JsonFgConfiguration extends ExtensionConfiguration, FeatureFormatConfiguration {

  /**
   * @langEn Activates support for the "jsonfg-plus" profile. In that profile, JSON-FG features with
   *     a "place" member will also include a GeoJSON geometry in the "geometry" member in WGS 84.
   *     Otherwise, the "geometry" member of a JSON-FG feature will be `null`, if the "place" member
   *     is present.
   * @langDe Aktiviert die Unterstützung für das "jsonfg-plus"-Profil. In diesem Profil enthalten
   *     JSON-FG-Features mit einem JSON-Member "place" auch eine GeoJSON-Geometrie im JSON-Member
   *     "geometry" im Koordinatenreferenzsystem WGS 84. Andernfalls ist "geometry" eines
   *     JSON-FG-Features `null`, wenn "place" vorhanden ist.
   * @default true
   * @since v4.5
   */
  @Nullable
  Boolean getSupportPlusProfile();

  /**
   * @langEn *Deprecated* (replaced by `supportPlusProfile`).
   * @langDe *Deprecated* (ersetzt durch `supportPlusProfile`).
   * @default true
   * @since v3.3
   */
  @Deprecated(since = "4.5", forRemoval = true)
  @Nullable
  Boolean getGeojsonCompatibility();

  /**
   * @langEn Features are often categorized by type. Typically, all features of the same type have
   *     the same schema and the same properties.
   *     <p>Many GIS clients depend on knowledge about the feature type when processing feature
   *     data. For example, when associating a style to a feature in order to render that feature on
   *     a map.
   *     <p>This option adds a "featureType" member with the specified value.
   *     <p>A value can include a template `{{type}}`, which will be replaced with the value of the
   *     feature property with `role: TYPE` in the provider schema of the feature type of the
   *     collection. The property must be of type `STRING`.
   *     <p>If the feature type in the provider schema includes an `objectType` value, the value
   *     will be used as the default. Otherwise, the default is `null`.
   * @langDe Features werden oft nach der Objektart kategorisiert. In der Regel haben alle Features
   *     derselben Art dasselbe Schema und dieselben Eigenschaften.
   *     <p>Viele GIS-Clients sind bei der Verarbeitung von Features auf das Wissen über den
   *     Objektart angewiesen. Zum Beispiel, wenn einem Feature ein Stil zugeordnet wird, um das
   *     Feature auf einer Karte darzustellen.
   *     <p>Diese Option fügt ein JSON-Member "featureType" mit dem angegebenen Wert hinzu.
   *     <p>Der Wert kann ein Template `{{type}}` enthalten, das durch den Wert der
   *     Objekteigenschaft mit `role: TYPE` im Provider-Schema der Objektart der Collection ersetzt
   *     wird. Die Eigenschaft muss vom Typ `STRING` sein.
   *     <p>Wenn der Objekttyp im Provider-Schema einen Wert für `objectType` hat, dann ist dieser
   *     Wert der Default. Ansonsten ist der Default `null`.
   * @default see description
   * @examplesAll 'Building'
   * @since v3.1
   */
  @Nullable
  Object getFeatureType();

  default String getEffectiveFeatureType(Optional<FeatureSchema> schema) {
    Object value = getFeatureType();
    if (value instanceof List) {
      if (!((List<?>) value).isEmpty()) {
        value = ((List<?>) value).get(0);
      } else {
        value = null;
      }
    }
    if (value instanceof String) {
      return (String) value;
    }
    if (Objects.isNull(value)) {
      return schema.flatMap(FeatureSchema::getObjectType).orElse(null);
    }
    return null;
  }

  /**
   * @langEn Adds the specified links to the `links` array of features. All values of the array must
   *     be a valid link object with `href` and `rel`.
   * @langDe Ergänzt den "links"-Array von Features um die angegebenen Links. Alle Werte des Arrays
   *     müssen ein gültiges Link-Objekt mit `href` und `rel` sein.
   * @default []
   * @since v3.1
   */
  @Nullable
  List<Link> getLinks();

  @Override
  default Builder getBuilder() {
    return new ImmutableJsonFgConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableJsonFgConfiguration.Builder()
        .from(source)
        .from(this)
        .defaultProfiles(
            this.getDefaultProfiles().isEmpty()
                ? ((FeatureFormatConfiguration) source).getDefaultProfiles()
                : this.getDefaultProfiles())
        .featureType(
            Objects.isNull(this.getFeatureType())
                ? ((JsonFgConfiguration) source).getFeatureType()
                : this.getFeatureType())
        .build();
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Value.Check
  default JsonFgConfiguration migrateGeojsonCompatibility() {
    if (Objects.nonNull(getGeojsonCompatibility())) {
      return new ImmutableJsonFgConfiguration.Builder()
          .from(this)
          .supportPlusProfile(getGeojsonCompatibility())
          .build();
    }

    return this;
  }
}
