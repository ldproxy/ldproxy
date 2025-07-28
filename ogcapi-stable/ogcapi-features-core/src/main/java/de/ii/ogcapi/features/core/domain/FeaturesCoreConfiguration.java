/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.domain.CachingConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.ImmutableEpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.FeatureTypeConfiguration;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock FEATURES_CORE
 * @examplesEn Example of specifications in the configuration file for the entire API (or in
 *     defaults):
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_CORE
 *   coordinatePrecision:
 *     metre: 2
 *     degree: 7
 * ```
 *     </code>
 *     <p>Example of the specifications in the configuration file for a feature collection:<code>
 * ```yaml
 * - buildingBlock: FEATURES_CORE
 *   enabled: true
 *   itemType: feature
 *   queryables:
 *     spatial:
 *     - geometry
 *     temporal:
 *     - date
 *     q:
 *     - name
 *     - region
 *     - subregion
 *     - cluster
 *     - village
 *     - searchfield1
 *     - searchfield2
 *     other:
 *     - registerId
 *     - area_ha
 *   embeddedFeatureLinkRels:
 *   - self
 * ```
 *     </code>
 * @examplesDe Beispiel für die Angaben in der Konfigurationsdatei für die gesamte API (oder in den
 *     Defaults):
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_CORE
 *   coordinatePrecision:
 *     metre: 2
 *     degree: 7
 * ```
 *     </code>
 *     <p>Beispiel für die Angaben in der Konfigurationsdatei für eine Feature Collection:
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_CORE
 *   coordinatePrecision:
 *     metre: 2
 *     degree: 7
 * ```
 *     </code>
 *     <p>Example of the specifications in the configuration file for a feature collection:<code>
 * ```yaml
 * - buildingBlock: FEATURES_CORE
 *   enabled: true
 *   itemType: feature
 *   queryables:
 *     spatial:
 *     - geometry
 *     temporal:
 *     - date
 *     q:
 *     - name
 *     - region
 *     - subregion
 *     - cluster
 *     - village
 *     - searchfield1
 *     - searchfield2
 *     other:
 *     - registerId
 *     - area_ha
 *   embeddedFeatureLinkRels:
 *   - self
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "FEATURES_CORE")
@JsonDeserialize(builder = ImmutableFeaturesCoreConfiguration.Builder.class)
public interface FeaturesCoreConfiguration
    extends ExtensionConfiguration,
        PropertyTransformations,
        CachingConfiguration,
        ProfilesConfiguration {

  abstract class Builder extends ExtensionConfiguration.Builder {}

  enum DefaultCrs {
    CRS84,
    CRS84h
  }

  enum ItemType {
    unknown,
    feature,
    record
  }

  int MINIMUM_PAGE_SIZE = 1;
  int DEFAULT_PAGE_SIZE = 10;
  int MAX_PAGE_SIZE = 10000;
  String PARAMETER_BBOX = "bbox";
  String PARAMETER_DATETIME = "datetime";
  String DATETIME_INTERVAL_SEPARATOR = "/";

  /**
   * @default true
   */
  @Nullable
  @Override
  Boolean getEnabled();

  /**
   * @langEn Id of the feature provider to use. Normally the feature provider and API ids are the
   *     same.
   * @langDe Identifiziert den verwendeten Feature-Provider. Standardmäßig besitzt der
   *     Feature-Provider dieselbe ID wie die API.
   * @default apiId
   */
  Optional<String> getFeatureProvider();

  /**
   * @langEn Id of the feature type to use as defined in the given feature provider. Normally the
   *     feature type and collection ids are the same.
   * @langDe Identifiziert die verwendete Objektart im Feature-Provider. Standardmäßig besitzt die
   *     Objektart dieselbe ID wie die Collection.
   * @default collectionId
   */
  Optional<String> getFeatureType();

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @Value.Default
  default List<String> getFeatureTypes() {
    return getFeatureType().isPresent()
        ? ImmutableList.of(getFeatureType().get())
        : ImmutableList.of();
  }

  /**
   * @langEn Default coordinate reference system, either `CRS84` for datasets with 2D geometries or
   *     `CRS84h` for datasets with 3D geometries.
   * @langDe Setzt das Standard-Koordinatenreferenzsystem, entweder 'CRS84' für einen Datensatz mit
   *     2D-Geometrien oder 'CRS84h' für einen Datensatz mit 3D-Geometrien.
   * @default CRS84
   */
  @Nullable
  DefaultCrs getDefaultCrs();

  /**
   * @langEn Minimum value for parameter `limit`.
   * @langDe Setzt den Minimalwert für den Parameter `limit`.
   * @default 1
   */
  @Nullable
  Integer getMinimumPageSize();

  /**
   * @langEn Default value for parameter `limit`.
   * @langDe Setzt den Defaultwert für den Parameter `limit`.
   * @default 10
   */
  @Nullable
  Integer getDefaultPageSize();

  /**
   * @langEn Maximum value for parameter `limit`.
   * @langDe Setzt den Maximalwert für den Parameter `limit`.
   * @default 10000
   */
  @Nullable
  Integer getMaximumPageSize();

  /**
   * @langEn Controls which links should be specified for each feature in the Features resource, if
   *     these exist. The values are the link relation types to be included. By default, links such
   *     as `self` or `alternate` are omitted from features in a FeatureCollection, this option can
   *     be used to add them if needed.
   * @langDe Steuert, welche Links bei jedem Feature in der Ressource "Features" angegeben werden
   *     sollen, sofern vorhanden. Die Werte sind die Link-Relation-Types, die berücksichtigt werden
   *     sollen. Standardmäßig werden Links wie `self` oder `alternate` bei den Features in einer
   *     FeatureCollection weggelassen, mit dieser Option können Sie bei Bedarf ergänzt werden.
   * @default []
   */
  Set<String> getEmbeddedFeatureLinkRels();

  /**
   * Validate the coordinates of the bbox or filter parameters against the domain of validity of the
   * coordinate reference system
   */
  @Nullable
  Boolean getValidateCoordinatesInQueries();

  Optional<ItemType> getItemType();

  /**
   * @langEn Controls whether coordinates are limited to a certain number of places depending on the
   *     coordinate reference system used. The unit of measurement and the corresponding number of
   *     decimal places must be specified. Example: `{ "metre" : 2, "degree" : 7 }`. Valid units of
   *     measurement are "metre" and "degree".
   * @langDe Steuert, ob Koordinaten in Abhängig des verwendeten Koordinatenreferenzsystems auf eine
   *     bestimmte Anzahl von Stellen begrenzt werden. Anzugeben ist die Maßeinheit und die
   *     zugehörige Anzahl der Nachkommastellen. Beispiel: `{ "metre" : 2, "degree" : 7 }`. Gültige
   *     Maßeinheiten sind "metre" (bzw. "meter") und "degree".
   * @default {}
   */
  Map<String, Integer> getCoordinatePrecision();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default EpsgCrs getDefaultEpsgCrs() {
    return ImmutableEpsgCrs.copyOf(
        getDefaultCrs() == DefaultCrs.CRS84h ? OgcCrs.CRS84h : OgcCrs.CRS84);
  }

  /**
   * @langEn Change the default value of the [profile parameter](features.md#query-parameters) for
   *     feature resources. The value is an object where the key is the id of a profile set, such as
   *     `rel`, and the value is the default profile for the profile set, e.g., `rel-as-key`. In
   *     addition, format-specific default profiles can be specified in the building block of each
   *     feature format. Those defaults have a higher priority.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](features.md#query-parameter) für
   *     Features. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `rel`, und der Wert das Standardprofil für den Profilsatz, z. B. `rel-as-key`. Darüber
   *     hinaus können für jedes Feature-Format im entsprechenden Baustein formatspezifische
   *     Standardprofile konfiguriert werden. Diese Standardprofile haben eine höhere Priorität.
   * @since v4.5
   * @default {"rel": "rel-as-link", "val": "val-as-code"}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  @Override
  default Builder getBuilder() {
    return new ImmutableFeaturesCoreConfiguration.Builder().from(this);
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    ImmutableFeaturesCoreConfiguration.Builder builder =
        new ImmutableFeaturesCoreConfiguration.Builder()
            .from(source)
            .from(this)
            .transformations(
                PropertyTransformations.super
                    .mergeInto((PropertyTransformations) source)
                    .getTransformations())
            .defaultProfiles(
                ProfilesConfiguration.super
                    .mergeInto((ProfilesConfiguration) source)
                    .getDefaultProfiles());

    Map<String, Integer> mergedCoordinatePrecision =
        new LinkedHashMap<>(((FeaturesCoreConfiguration) source).getCoordinatePrecision());
    mergedCoordinatePrecision.putAll(getCoordinatePrecision());
    builder.coordinatePrecision(mergedCoordinatePrecision);

    // keep the rels from the parent configuration and just add new rels
    builder.embeddedFeatureLinkRels(
        ImmutableSet.<String>builder()
            .addAll(((FeaturesCoreConfiguration) source).getEmbeddedFeatureLinkRels())
            .addAll(this.getEmbeddedFeatureLinkRels())
            .build());

    return builder.build();
  }

  static String getCollectionId(OgcApiDataV2 apiData, String featureType) {
    return apiData.getCollections().values().stream()
        .filter(
            collection ->
                collection
                    .getExtension(FeaturesCoreConfiguration.class)
                    .flatMap(FeaturesCoreConfiguration::getFeatureType)
                    .filter(featureType::equals)
                    .isPresent())
        .findFirst()
        .map(FeatureTypeConfiguration::getId)
        .orElse(featureType);
  }
}
