/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.projections.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.SchemaInfo;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.tiles.domain.TileGenerationUserParameter;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.tiles.domain.ImmutableTileGenerationParametersTransient;
import de.ii.xtraplatform.tiles.domain.TileGenerationSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @title exclude-properties
 * @endpoints Features, Feature, Vector Tile
 * @langEn The properties that should be excluded for each feature. The parameter value is a
 *     comma-separated list of property names. By default, all feature properties with a value are
 *     returned, except the properties with the role `AUXILIARY` in the feature schema or, if set,
 *     the properties in `defaultExcludedProperties`; this default does not apply to vector tiles.
 *     An empty value excludes no properties. The feature id cannot be excluded. The parameter must
 *     not be used together with the "properties" parameter; if "properties" is set, the default
 *     does not apply.
 * @langDe Die Eigenschaften, die für jedes Feature ausgeschlossen werden sollen. Der Parameterwert
 *     ist eine kommagetrennte Liste von Eigenschaftsnamen. Standardmäßig werden alle
 *     Feature-Eigenschaften mit einem Wert zurückgegeben, außer den Eigenschaften mit der Rolle
 *     `AUXILIARY` im Feature-Schema oder, falls angegeben, den Eigenschaften in
 *     `defaultExcludedProperties`; dieser Standard gilt nicht für Vektorkacheln. Ein leerer Wert
 *     schließt keine Eigenschaften aus. Die Feature-Id kann nicht ausgeschlossen werden. Der
 *     Parameter darf nicht zusammen mit dem Parameter "properties" verwendet werden; ist
 *     "properties" angegeben, gilt der Standard nicht.
 */
@Singleton
@AutoBind
public class QueryParameterExcludeProperties extends OgcApiQueryParameterBase
    implements TypedQueryParameter<List<String>>,
        FeatureQueryParameter,
        TileGenerationUserParameter {

  private final SchemaInfo schemaInfo;
  private final SchemaValidator schemaValidator;
  private final FeaturesCoreProviders providers;

  @Inject
  public QueryParameterExcludeProperties(
      SchemaInfo schemaInfo, SchemaValidator schemaValidator, FeaturesCoreProviders providers) {
    this.schemaInfo = schemaInfo;
    this.schemaValidator = schemaValidator;
    this.providers = providers;
  }

  @Override
  public String getId(String collectionId) {
    return "exclude-properties_" + collectionId;
  }

  @Override
  public String getName() {
    return "exclude-properties";
  }

  @Override
  public String getDescription() {
    return "The properties that should be excluded for each feature. The parameter value is a comma-separated list of property names. By default, the properties in the default value of the parameter are excluded; an empty value excludes no properties. The feature id cannot be excluded. The parameter must not be used together with the 'properties' parameter; if 'properties' is set, the default does not apply.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath)
        || "/collections/{collectionId}/items/{featureId}".equals(definitionPath)
        || "/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}"
            .equals(definitionPath);
  }

  private ConcurrentMap<Integer, ConcurrentMap<String, Schema<?>>> schemaMap =
      new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schemaMap
        .computeIfAbsent(apiData.hashCode(), ignore -> new ConcurrentHashMap<>())
        .computeIfAbsent("*", ignore -> new ArraySchema().items(new StringSchema().minLength(1)));
  }

  // the feature id cannot be excluded
  private List<String> getExcludablePropertyNames(OgcApiDataV2 apiData, String collectionId) {
    Optional<String> idProperty =
        Optional.ofNullable(apiData.getCollections().get(collectionId))
            .flatMap(collectionData -> providers.getFeatureSchema(apiData, collectionData))
            .flatMap(FeatureSchema::getIdProperty)
            .map(FeatureSchema::getFullPathAsString);
    return schemaInfo.getPropertyNames(apiData, collectionId, true, false).stream()
        .filter(name -> idProperty.map(id -> !id.equals(name)).orElse(true))
        .toList();
  }

  /**
   * The properties that are excluded if a request sets neither this parameter nor "properties": the
   * properties in {@code defaultExcludedProperties} or, if none are set, the properties with the
   * role {@code AUXILIARY}.
   */
  List<String> getDefaultExcludedProperties(OgcApiDataV2 apiData, String collectionId) {
    List<String> excludable = getExcludablePropertyNames(apiData, collectionId);
    List<String> configured =
        apiData
            .getExtension(ProjectionsConfiguration.class, collectionId)
            .map(ProjectionsConfiguration::getDefaultExcludedProperties)
            .orElse(List.of());
    List<String> auxiliary =
        configured.isEmpty()
            ? Optional.ofNullable(apiData.getCollections().get(collectionId))
                .flatMap(collectionData -> providers.getFeatureSchema(apiData, collectionData))
                .map(
                    schema ->
                        schema.getAllNestedProperties().stream()
                            .filter(FeatureSchema::isAuxiliary)
                            .map(FeatureSchema::getFullPathAsString)
                            .toList())
                .orElse(List.of())
            : List.of();
    return defaultExcludedProperties(excludable, configured, auxiliary);
  }

  // the configured default or, if none is configured, the auxiliary properties
  static List<String> defaultExcludedProperties(
      List<String> excludable, List<String> configured, List<String> auxiliary) {
    return (configured.isEmpty() ? auxiliary : configured)
        .stream().filter(excludable::contains).toList();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return schemaMap
        .computeIfAbsent(apiData.hashCode(), ignore -> new ConcurrentHashMap<>())
        .computeIfAbsent(
            collectionId,
            ignore -> {
              // an empty value excludes no properties, also none of the default
              ArraySchema schema =
                  new ArraySchema()
                      .items(
                          new StringSchema()
                              ._enum(getExcludablePropertyNames(apiData, collectionId)));
              List<String> defaultExcluded = getDefaultExcludedProperties(apiData, collectionId);
              if (!defaultExcluded.isEmpty()) {
                schema.setDefault(defaultExcluded);
              }
              return schema;
            });
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProjectionsConfiguration.class;
  }

  @Override
  public List<String> parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    try {
      return Splitter.on(',').omitEmptyStrings().trimResults().splitToList(value);
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("Invalid value for query parameter '%s'.", getName()), e);
    }
  }

  @Override
  public void applyTo(
      ImmutableFeatureQuery.Builder queryBuilder,
      QueryParameterSet parameters,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData) {
    excludedProperties(
            parameters.getValue(this),
            parameters.getTypedValues().containsKey("properties"),
            () -> getDefaultExcludedProperties(apiData, collectionData.getId()))
        .ifPresent(
            excludeProperties -> {
              schemaInfo.getPropertyNames(apiData, collectionData.getId(), true, false).stream()
                  .filter(propertyName -> !excludeProperties.contains(propertyName))
                  .forEach(queryBuilder::addFields);

              if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                    "Excluded properties: {}; selected properties: {}",
                    String.join(", ", excludeProperties),
                    schemaInfo
                        .getPropertyNames(apiData, collectionData.getId(), true, false)
                        .stream()
                        .filter(propertyName -> !excludeProperties.contains(propertyName))
                        .collect(Collectors.joining(", ")));
              }
            });
  }

  // the requested exclusions or, if the request does not select the properties itself, the default
  static Optional<List<String>> excludedProperties(
      Optional<List<String>> requested,
      boolean propertiesRequested,
      Supplier<List<String>> defaultExcluded) {
    if (requested.isPresent() || propertiesRequested) {
      return requested;
    }
    List<String> defaults = defaultExcluded.get();
    return defaults.isEmpty() ? Optional.empty() : Optional.of(defaults);
  }

  // the default does not apply to tiles: their generation does not evaluate parameter defaults
  @Override
  public void applyTo(
      ImmutableTileGenerationParametersTransient.Builder userParametersBuilder,
      QueryParameterSet parameters,
      Optional<TileGenerationSchema> generationSchema) {
    parameters
        .getValue(this)
        .ifPresent(
            excludeProperties -> {
              generationSchema
                  .map(TileGenerationSchema::getProperties)
                  .map(Map::keySet)
                  .ifPresent(
                      keys ->
                          keys.stream()
                              .filter(propertyName -> !excludeProperties.contains(propertyName))
                              .forEach(userParametersBuilder::addFields));
            });
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return ProjectionsBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return ProjectionsBuildingBlock.SPEC;
  }
}
