/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.queryables.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.CachingConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock QUERYABLES
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: QUERYABLES
 *   enabled: true
 *   included:
 *     - '*'
 *   excluded:
 *     - foo
 *     - bar
 *   pathSeparator: UNDERSCORE
 *   asQueryParameters: false
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "QUERYABLES")
@JsonDeserialize(builder = ImmutableQueryablesConfiguration.Builder.class)
public interface QueryablesConfiguration
    extends ExtensionConfiguration, CachingConfiguration, ProfilesConfiguration {

  enum PathSeparator {
    DOT("."),
    UNDERSCORE("_");

    private final String label;

    PathSeparator(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /**
   * @default true
   */
  @Nullable
  @Override
  Boolean getEnabled();

  /**
   * @langEn The list of properties that can be used in CQL2 filter expressions and/or for which
   *     filtering query parameters are provided for a collection. Properties that are not of type
   *     `OBJECT` or `OBJECT_ARRAY` are eligible as queryables unless `isQueryable` is set to
   *     `false` for the property. The special value `*` includes all eligible properties as
   *     queryables. By default, no property is queryable.
   * @langDe Die Liste der Eigenschaften, die in CQL2-Filterausdrücken verwendet werden können
   *     und/oder für die filternde Abfrageparameter für eine Collection bereitgestellt werden.
   *     Eigenschaften, die nicht vom Typ `OBJECT` oder `OBJECT_ARRAY` sind, kommen als Queryables
   *     in Frage, es sei denn `isQueryable` ist für die Eigenschaft auf `false` gesetzt. Der
   *     spezielle Wert `*` schließt alle in Frage kommenden Eigenschaften als abfragbar ein.
   *     Standardmäßig ist keine Eigenschaft abfragbar.
   * @default []
   * @since v3.4
   */
  List<String> getIncluded();

  /**
   * @langEn The list of properties that would be queryables based on `included`, but which should
   *     not be queryables.
   * @langDe Die Liste der Eigenschaften, die aufgrund von `included` abfragbar wären, aber nicht
   *     abfragbar sein sollen.
   * @default []
   * @since v3.4
   */
  List<String> getExcluded();

  /**
   * @langEn The character that is used as the path separator in case of object-valued properties.
   *     Either `DOT` or `UNDERSCORE`.
   * @langDe Das Zeichen, das im Falle von objektwertigen Eigenschaften als Pfadseparator verwendet
   *     wird. Entweder `DOT` (Punkt) oder `UNDERSCORE` (Unterstrich).
   * @default DOT
   * @since v3.4
   */
  @Nullable
  PathSeparator getPathSeparator();

  /**
   * @langEn If `true`, the Queryables endpoint will be enabled.
   * @langDe Bei `true` wird die Queryables-Ressource aktiviert.
   * @default true
   * @since v3.4
   */
  @Nullable
  Boolean getEnableEndpoint();

  @Value.Derived
  @Value.Auxiliary
  @JsonIgnore
  default boolean endpointIsEnabled() {
    return Objects.requireNonNullElse(getEnableEndpoint(), false);
  }

  /**
   * @langEn If `true`, all queryables with a simple value (string, number or boolean) will be
   *     provided query parameters to filter features.
   * @langDe Bei `true` werden alle Queryables mit einem einfachen Wert (String, Zahl oder Boolean)
   *     als Query-Parameter zum Filtern der Features bereitgestellt.
   * @default true
   * @since v3.4
   */
  @Nullable
  Boolean getAsQueryParameters();

  @Value.Derived
  @Value.Auxiliary
  @JsonIgnore
  default boolean provideAsQueryParameters() {
    return Objects.requireNonNullElse(getAsQueryParameters(), false);
  }

  /**
   * @langEn Change the default value of the [profile parameter](schemas.md#query-parameters) for
   *     schema resources. The value is an object where the key is the id of a profile set, such as
   *     `codelist`, and the value is the default profile for the profile set, e.g.,
   *     `codelists-inline`. In addition, format-specific default profiles can be specified in the
   *     building block of each schema format. Those defaults have a higher priority.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](schemas.md#query-parameter) für
   *     Schemas. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `codelist`, und der Wert das Standardprofil für den Profilsatz, z. B.
   *     `codelists-inline`. Darüber hinaus können für jedes Schema-Format im entsprechenden
   *     Baustein formatspezifische Standardprofile konfiguriert werden. Diese Standardprofile haben
   *     eine höhere Priorität.
   * @since v4.5
   * @default {"codelist": "codelists-ref"}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  default Map<String, FeatureSchema> getQueryables(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      FeaturesCoreProviders providers) {
    return providers
        .getFeatureSchema(apiData, collectionData)
        .map(schema -> getQueryables(apiData, collectionData, schema, providers, true))
        .orElse(ImmutableMap.of());
  }

  default Map<String, FeatureSchema> getQueryables(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      FeatureSchema schema,
      FeaturesCoreProviders providers,
      boolean cleanupKeys) {
    return getQueryablesSchema(apiData, collectionData, schema, providers, cleanupKeys)
        .getAllNestedProperties()
        .stream()
        .filter(FeatureSchema::queryable)
        .map(
            subschema ->
                new SimpleImmutableEntry<>(
                    subschema.getFullPathAsString(getPathSeparator().toString()), subschema))
        .collect(
            ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue, (first, second) -> second));
  }

  default Map<String, FeatureSchema> getQueryables(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      FeatureSchema schema,
      FeaturesCoreProviders providers) {
    return getQueryables(apiData, collectionData, schema, providers, true);
  }

  default FeatureSchema getQueryablesSchema(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      FeatureSchema schema,
      FeaturesCoreProviders providers,
      boolean cleanupKeys) {
    FeatureQueries featureQueries =
        providers.getFeatureProviderOrThrow(apiData, collectionData, FeatureProvider::queries);

    return featureQueries.getQueryablesSchema(
        schema, getIncluded(), getExcluded(), getPathSeparator().toString(), cleanupKeys);
  }

  default FeatureSchema getQueryablesSchema(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      FeatureSchema schema,
      FeaturesCoreProviders providers) {
    return getQueryablesSchema(apiData, collectionData, schema, providers, true);
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableQueryablesConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableQueryablesConfiguration.Builder()
        .from(source)
        .from(this)
        .included(
            Stream.concat(
                    ((QueryablesConfiguration) source).getIncluded().stream(),
                    getIncluded().stream())
                .distinct()
                .collect(Collectors.toList()))
        .excluded(
            Stream.concat(
                    ((QueryablesConfiguration) source).getExcluded().stream(),
                    getExcluded().stream())
                .distinct()
                .collect(Collectors.toList()))
        .defaultProfiles(
            ProfilesConfiguration.super
                .mergeInto((ProfilesConfiguration) source)
                .getDefaultProfiles())
        .build();
  }
}
