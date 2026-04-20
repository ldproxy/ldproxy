/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.filter.api;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ItemTypeSpecificConformanceClass;
import de.ii.ogcapi.filter.app.FilterBuildingBlock;
import de.ii.ogcapi.filter.domain.FilterConfiguration;
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
import de.ii.xtraplatform.cql.domain.And;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql.Format;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.CustomFunction;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureInfo;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.tiles.domain.ImmutableTileGenerationParametersTransient;
import de.ii.xtraplatform.tiles.domain.TileGenerationSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

// TODO
// @endpoints {@link de.ii.ogcapi.features.core.app.EndpointFeatures},{@link
// de.ii.ogcapi.tiles.infra.EndpointVectorTileDataset},{@link
// de.ii.ogcapi.tiles.infra.EndpointVectorTileCollection}

/**
 * @title filter
 * @endpoints Features, Vector Tile
 * @langEn The filter expression in the filter language declared in `filter-lang`. Coordinates are
 *     in the coordinate reference system declared in `filter-crs`.
 * @langDe Der Filterausdruck in der in `filter-lang` angegebenen Filtersprache. Die Koordinaten
 *     sind in dem in `filter-crs` angegebenen Koordinatenreferenzsystem.
 */
@Singleton
@AutoBind
public class QueryParameterFilter extends OgcApiQueryParameterBase
    implements ItemTypeSpecificConformanceClass,
        TileGenerationUserParameter,
        FeatureQueryParameter,
        TypedQueryParameter<Cql2Expression> {

  private final FeaturesCoreProviders providers;
  private final SchemaValidator schemaValidator;
  private final CrsInfo crsInfo;
  private final Cql cql;
  private final CrsTransformerFactory crsTransformerFactory;

  @Inject
  public QueryParameterFilter(
      FeaturesCoreProviders providers,
      SchemaValidator schemaValidator,
      CrsInfo crsInfo,
      Cql cql,
      CrsTransformerFactory crsTransformerFactory) {
    this.providers = providers;
    this.schemaValidator = schemaValidator;
    this.crsInfo = crsInfo;
    this.cql = cql;
    this.crsTransformerFactory = crsTransformerFactory;
  }

  private boolean supportsCql2(OgcApiDataV2 apiData) {
    return providers
        .getFeatureProvider(apiData, FeatureProvider::queries)
        .map(FeatureQueries::supportsCql2)
        .orElse(false);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData) && supportsCql2(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId) && supportsCql2(apiData);
  }

  @Override
  public String getName() {
    return "filter";
  }

  @Override
  public String getDescription() {
    return "Filter features in the collection using the query expression in the parameter value. Filter expressions "
        + "are written in the [Common Query Language (CQL2)](https://docs.ogc.org/is/21-065r2/21-065r2.html), "
        + "which is an OGC Standard.\n\n"
        + "The recommended language for this query parameter is CQL2 Text (`filter-lang=cql2-text`).\n\n"
        + "CQL2 Text expressions are similar to SQL expressions and also support spatial, temporal and array predicates. "
        + "All property references must be queryables of the collection and must be declared in the Queryables sub-resource "
        + "of the collection.\n\n"
        + "The following are examples of CQL Text expressions:\n\n"
        + "* Logical operators (`AND`, `OR`, `NOT`) are supported\n"
        + "* Simple comparison predicates (`=`, `<>`, `<`, `>`, `<=`, `>=`):\n"
        + "  * `address.LocalityName = 'Bonn'`\n"
        + "  * `measuredHeight > 10`\n"
        + "  * `storeysAboveGround <= 4`\n"
        + "  * `creationDate > DATE('2017-12-31')`\n"
        + "  * `creationDate < DATE('2018-01-01')`\n"
        + "  * `creationDate >= DATE('2018-01-01') AND creationDate <= DATE('2018-12-31')`\n"
        + "* Advanced comparison operators (`LIKE`, `BETWEEN`, `IN`, `IS NULL`):\n"
        + "  * `name LIKE '%Kirche%'`\n"
        + "  * `measuredHeight BETWEEN 10 AND 20`\n"
        + "  * `address.LocalityName IN ('Bonn', 'Köln', 'Düren')`\n"
        + "  * `address.LocalityName NOT IN ('Bonn', 'Köln', 'Düren')`\n"
        + "  * `name IS NULL`\n"
        + "  * `name IS NOT NULL`\n"
        + "* Spatial operators (the standard Simple Feature operators, e.g., `S_INTERSECTS`, `S_WITHIN`):\n"
        + "  * `S_INTERSECTS(bbox, POLYGON((8 52, 9 52, 9 53, 8 53, 8 52)))`\n"
        + "* Temporal operators (e.g., `T_AFTER`, `T_BEFORE`, `T_INTERSECTS`)\n"
        + "  * `T_AFTER(creationDate, DATE('2018-01-01'))`\n"
        + "  * `T_BEFORE(creationDate, DATE('2018-01-01'))`\n"
        + "  * `T_INTERSECTS(creationDate, INTERVAL('2018-01-01','2018-12-31'))`\n"
        + "  * `T_INTERSECTS(INTERVAL(begin,end), INTERVAL('2018-01-01','2018-12-31'))`";
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    if (apiValidation == MODE.NONE) return ValidationResult.of();

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    for (String collectionWithoutQueryables :
        api.getData().getCollections().entrySet().stream()
            .filter(
                entry ->
                    entry
                            .getValue()
                            .getExtension(getBuildingBlockConfigurationType())
                            .map(ExtensionConfiguration::isEnabled)
                            .orElse(false)
                        && !entry
                            .getValue()
                            .getExtension(QueryablesConfiguration.class)
                            .map(ExtensionConfiguration::isEnabled)
                            .orElse(false))
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableList())) {
      builder.addStrictErrors(
          MessageFormat.format(
              "The FILTER module is enabled for collection ''{0}'', but the QUERYABLES module is not enabled.",
              collectionWithoutQueryables));
    }

    return builder.build();
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items")
        || definitionPath.equals("/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}")
        || definitionPath.equals(
            "/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}");
  }

  private final Schema<String> schema = new StringSchema();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();

    providers
        .getFeatureProvider(apiData)
        .ifPresent(
            provider -> {
              if (provider.queries().isSupported() && provider.queries().get().supportsCql2()) {
                builder.add(
                    "http://www.opengis.net/spec/ogcapi-features-3/1.0/conf/filter",
                    "http://www.opengis.net/spec/cql2/1.0/conf/basic-cql2",
                    "http://www.opengis.net/spec/cql2/1.0/conf/advanced-comparison-operators",
                    "http://www.opengis.net/spec/cql2/1.0/conf/case-insensitive-comparison",
                    "http://www.opengis.net/spec/cql2/1.0/conf/basic-spatial-functions",
                    "http://www.opengis.net/spec/cql2/1.0/conf/basic-spatial-functions-plus",
                    "http://www.opengis.net/spec/cql2/1.0/conf/spatial-functions",
                    "http://www.opengis.net/spec/cql2/1.0/conf/temporal-functions",
                    "http://www.opengis.net/spec/cql2/1.0/conf/array-functions",
                    "http://www.opengis.net/spec/cql2/1.0/conf/property-property",
                    "http://www.opengis.net/spec/cql2/1.0/conf/cql2-text",
                    "http://www.opengis.net/spec/cql2/1.0/conf/cql2-json");
                if (provider.queries().get().supportsAccenti())
                  builder.add(
                      "http://www.opengis.net/spec/cql2/1.0/conf/accent-insensitive-comparison");
              }
            });

    if (isItemTypeUsed(apiData, FeaturesCoreConfiguration.ItemType.feature))
      builder.add("http://www.opengis.net/spec/ogcapi-features-3/1.0/conf/features-filter");

    if (isItemTypeUsed(apiData, FeaturesCoreConfiguration.ItemType.record))
      builder.add(
          "http://www.opengis.net/spec/ogcapi-features-3/1.0/conf/features-filter",
          "http://www.opengis.net/spec/ogcapi-records-1/0.0/conf/cql-filter");

    return builder.build();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FilterConfiguration.class;
  }

  @Override
  public boolean isFilterParameter() {
    return true;
  }

  @Override
  public int getPriority() {
    // wait for parsed results of filter-lang and filter-crs
    return 2;
  }

  @Override
  public Cql2Expression parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    FeatureTypeConfigurationOgcApi collectionData =
        optionalCollectionData.orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "The parameter '%s' could not be processed, no collection provided.",
                        getName())));

    Cql2Expression cql2Expression;
    Cql.Format filterLang =
        Objects.requireNonNullElse((Format) typedValues.get("filter-lang"), Format.TEXT);
    EpsgCrs filterCrs =
        Objects.requireNonNullElse((EpsgCrs) typedValues.get("filter-crs"), OgcCrs.CRS84);
    try {
      cql2Expression = cql.read(value, filterLang, filterCrs);
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("The parameter '%s' is invalid", getName()), e);
    }

    Map<String, FeatureSchema> queryables =
        collectionData
            .getExtension(QueryablesConfiguration.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "The parameter '%s' could not be processed, queryables for collection '%s' could not be determined.",
                            getName(), collectionData.getId())))
            .getQueryables(api.getData(), collectionData, providers);

    List<String> invalidProperties = cql.findInvalidProperties(cql2Expression, queryables.keySet());
    if (!invalidProperties.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The parameter '%s' is invalid. Unknown or forbidden properties used: %s.",
              getName(), String.join(", ", invalidProperties)));
    }

    // will throw an error, if there is a type mismatch
    Map<String, String> propertyTypes =
        queryables.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Entry::getKey, entry -> entry.getValue().getType().toString()));

    checkTypes(cql2Expression, propertyTypes, api, collectionData);

    if (collectionData
        .getExtension(FeaturesCoreConfiguration.class)
        .map(FeaturesCoreConfiguration::getValidateCoordinatesInQueries)
        .orElse(false)) {
      cql.checkCoordinates(
          cql2Expression,
          crsTransformerFactory,
          crsInfo,
          filterCrs,
          providers
              .getFeatureProvider(api.getData(), collectionData)
              .map(FeatureProvider::info)
              .flatMap(FeatureInfo::getCrs)
              .orElse(null));
    }

    return cql2Expression;
  }

  private void checkTypes(
      Cql2Expression cql2Expression,
      Map<String, String> propertyTypes,
      OgcApi api,
      FeatureTypeConfigurationOgcApi collectionData) {
    List<CustomFunction> customFunctions = getCustomFunctions(api, collectionData);

    if (!customFunctions.isEmpty()) {
      cql.checkTypes(cql2Expression, propertyTypes, customFunctions);
    } else {
      cql.checkTypes(cql2Expression, propertyTypes);
    }
  }

  private List<CustomFunction> getCustomFunctions(
      OgcApi api, FeatureTypeConfigurationOgcApi collectionData) {
    return providers
        .getFeatureProvider(api.getData(), collectionData)
        .map(FeatureProvider::queries)
        .filter(de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability::isSupported)
        .map(de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability::get)
        .map(FeatureQueries::getCql2Functions)
        .orElse(List.of());
  }

  @Override
  public Cql2Expression mergeValues(Object value1, Object value2) {
    if (Objects.isNull(value1)) {
      return (Cql2Expression) value2;
    } else if (Objects.isNull(value2)) {
      return (Cql2Expression) value1;
    }
    return And.of((Cql2Expression) value1, (Cql2Expression) value2);
  }

  @Override
  public void applyTo(
      ImmutableTileGenerationParametersTransient.Builder userParametersBuilder,
      QueryParameterSet parameters,
      Optional<TileGenerationSchema> generationSchema) {
    if (parameters.getTypedValues().containsKey(getName()) && generationSchema.isPresent()) {
      userParametersBuilder.addFilters((Cql2Expression) parameters.getTypedValues().get(getName()));
    }
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return FilterBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return FilterBuildingBlock.SPEC;
  }
}
