/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.sorting.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.ItemType;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ItemTypeSpecificConformanceClass;
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
import de.ii.ogcapi.sorting.domain.SortingConfiguration;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery.Builder;
import de.ii.xtraplatform.features.domain.SortKey;
import de.ii.xtraplatform.features.domain.SortKey.Direction;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @title sortby
 * @endpoints Features
 * @langEn If the parameter is specified, the features are returned sorted according to the
 *     attributes specified in a comma-separated list. The attribute name can be preceded by `+`
 *     (ascending, the default behavior) or `-` (descending). Example: `sortby=type,-name`. Only the
 *     sortable properties of the collection are accepted; the parameter is not available for
 *     collections without sortable properties.
 * @langDe Ist der Parameter angegeben, werden die Features sortiert zurückgegeben. Sortiert wird
 *     nach den in einer kommaseparierten Liste angegebenen Attributen. Dem Attributnamen kann ein
 *     `+` (aufsteigend, das Standardverhalten) oder ein `-` (absteigend) vorangestellt werden.
 *     Beispiel: `sortby=type,-name`. Es werden nur die sortierbaren Eigenschaften der Collection
 *     akzeptiert; für Collections ohne sortierbare Eigenschaften steht der Parameter nicht zur
 *     Verfügung.
 */
@Singleton
@AutoBind
public class QueryParameterSortbyFeatures extends OgcApiQueryParameterBase
    implements ItemTypeSpecificConformanceClass,
        FeatureQueryParameter,
        TypedQueryParameter<List<SortKey>> {

  static final Splitter KEYS_SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();
  private final SchemaValidator schemaValidator;
  private final FeaturesCoreProviders providers;
  private final ConcurrentMap<Integer, ConcurrentMap<String, Schema<?>>> schemaMap;

  @Inject
  QueryParameterSortbyFeatures(SchemaValidator schemaValidator, FeaturesCoreProviders providers) {
    super();
    this.schemaValidator = schemaValidator;
    this.providers = providers;
    this.schemaMap = new ConcurrentHashMap<>();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();

    if (isItemTypeUsed(apiData, ItemType.feature)) {
      builder.add(
          "http://www.opengis.net/spec/ogcapi-features-8/0.0/conf/sorting",
          "http://www.opengis.net/spec/ogcapi-features-8/0.0/conf/features-sorting");
    }

    if (isItemTypeUsed(apiData, ItemType.record)) {
      builder.add("http://www.opengis.net/spec/ogcapi-records-1/0.0/conf/sorting");
    }

    return builder.build();
  }

  @Override
  public String getId(String collectionId) {
    return "sortby_" + collectionId;
  }

  @Override
  public String getName() {
    return "sortby";
  }

  @Override
  public List<SortKey> parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      List<SortKey> sortKeys =
          optionalCollectionData
              .map(collectionData -> collectionData.getExtension(SortingConfiguration.class))
              .flatMap(cfg -> cfg.map(SortingConfiguration::getDefaultSortby))
              .map(keys -> keys.stream().map(QueryParameterSortbyFeatures::getSortKey).toList())
              .orElse(List.of());
      if (!sortKeys.isEmpty()) {
        return sortKeys;
      }

      // no default value
      return null;
    }

    // the validation against the schema has verified that only valid properties are listed
    ImmutableList.Builder<SortKey> builder = new ImmutableList.Builder<>();
    for (String key : KEYS_SPLITTER.split(value)) {
      builder.add(getSortKey(key));
    }
    return builder.build();
  }

  private static SortKey getSortKey(String key) {
    if (key.startsWith("-")) {
      return SortKey.of(key.substring(1), Direction.DESCENDING);
    } else {
      return SortKey.of(key.startsWith("+") ? key.substring(1) : key);
    }
  }

  @Override
  public void applyTo(
      Builder queryBuilder,
      QueryParameterSet parameters,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData) {
    parameters.getValue(this).ifPresent(queryBuilder::sortKeys);
  }

  @Override
  public String getDescription() {
    return "Sort the results based on the properties identified by this parameter. "
        + "The parameter value is a comma-separated list of property names that can be used to sort results (sortables), "
        + "where each parameter name "
        + "may be preceeded by a '+' (ascending, default) or '-' (descending).";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    if (!apiData.isCollectionEnabled(collectionId)) {
      return false;
    }
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    if (!isExtensionEnabled(collectionData, SortingConfiguration.class)) {
      return false;
    }
    boolean hasDefaultSortby =
        collectionData
            .getExtension(SortingConfiguration.class)
            .map(cfg -> !cfg.getDefaultSortby().isEmpty())
            .orElse(false);
    return hasDefaultSortby || !getSortables(apiData, collectionData).isEmpty();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schemaMap
        .computeIfAbsent(apiData.hashCode(), ignore -> new ConcurrentHashMap<>())
        .computeIfAbsent("*", ignore -> new ArraySchema().items(new StringSchema()));
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return schemaMap
        .computeIfAbsent(apiData.hashCode(), ignore -> new ConcurrentHashMap<>())
        .computeIfAbsent(
            collectionId,
            ignore -> {
              FeatureTypeConfigurationOgcApi collectionData =
                  Objects.requireNonNull(apiData.getCollections().get(collectionId));
              List<String> enums =
                  getSortables(apiData, collectionData).stream()
                      .map(p -> ImmutableList.of(p, "+" + p, "-" + p))
                      .flatMap(Collection::stream)
                      .collect(Collectors.toUnmodifiableList());
              StringSchema sortableSchema = new StringSchema();
              ArraySchema arraySchema = new ArraySchema().items(sortableSchema);
              if (enums.isEmpty()) {
                // no sortable properties, no sort key is valid
                arraySchema.maxItems(0);
              } else {
                sortableSchema._enum(enums);
              }
              return arraySchema;
            });
  }

  private List<String> getSortables(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return collectionData
        .getExtension(SortingConfiguration.class)
        .flatMap(
            cfg ->
                providers
                    .getFeatureSchema(apiData, collectionData)
                    .map(
                        featureSchema ->
                            cfg
                                .getSortables(apiData, collectionData, featureSchema, providers)
                                .keySet()
                                .stream()
                                .collect(Collectors.toUnmodifiableList())))
        .orElse(ImmutableList.of());
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SortingConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return SortingBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return SortingBuildingBlock.SPEC;
  }
}
