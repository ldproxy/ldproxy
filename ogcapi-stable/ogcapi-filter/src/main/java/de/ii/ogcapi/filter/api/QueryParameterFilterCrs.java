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
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.filter.app.FilterBuildingBlock;
import de.ii.ogcapi.filter.domain.FilterConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title filter-crs
 * @endpoints Features, Vector Tile
 * @langEn Specifies which of the supported coordinate reference systems is used to encode
 *     coordinates in the filter expression in parameter `filter`. Default is WGS84
 *     longitude/latitude (CRS84).
 * @langDe Gibt an, welches der unterstützten Koordinatenreferenzsysteme zur Kodierung von
 *     Koordinaten im Filterausdruck verwendet wird (Parameter `filter`). Standardwert ist WGS84
 *     Längen-/Breitengrad (CRS84).
 */
@Singleton
@AutoBind
public class QueryParameterFilterCrs extends OgcApiQueryParameterBase
    implements TypedQueryParameter<EpsgCrs> {

  public static final String FILTER_CRS = "filter-crs";
  public static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";

  private final CrsSupport crsSupport;
  private final FeaturesCoreProviders providers;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterFilterCrs(
      CrsSupport crsSupport, FeaturesCoreProviders providers, SchemaValidator schemaValidator) {
    this.crsSupport = crsSupport;
    this.providers = providers;
    this.schemaValidator = schemaValidator;
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
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items")
        || definitionPath.equals("/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}")
        || definitionPath.equals(
            "/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}");
  }

  @Override
  public String getName() {
    return "filter-crs";
  }

  @Override
  public String getDescription() {
    return "Specify which of the supported CRSs to use to encode geometric values in a filter expression (parameter 'filter'). Default is WGS84 longitude/latitude.";
  }

  @Override
  public EpsgCrs parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    EpsgCrs filterCrs;
    try {
      filterCrs = Objects.nonNull(value) ? EpsgCrs.fromString(value) : OgcCrs.CRS84;
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("The parameter '%s' is invalid: %s", getName(), e.getMessage()), e);
    }
    // CRS84 is always supported
    if (collectionData
            .map(cd -> !crsSupport.isSupported(api.getData(), cd, filterCrs))
            .orElse(!crsSupport.isSupported(api.getData(), filterCrs))
        && !filterCrs.equals(OgcCrs.CRS84)) {
      throw new IllegalArgumentException(
          String.format(
              "The parameter '%s' is invalid: the crs '%s' is not supported",
              getName(), filterCrs.toUriString()));
    }
    return filterCrs;
  }

  private ConcurrentMap<Integer, ConcurrentMap<String, Schema<?>>> schemaMap =
      new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey("*")) {
      // TODO: only include 2D (variants) of the CRSs
      String defaultCrs = CRS84 /* TODO support 4 or 6 numbers
            apiData.getExtension(FeaturesCoreConfiguration.class, collectionId)
                .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
                .map(ImmutableEpsgCrs::toUriString)
                .orElse(CRS84) */;
      List<String> crsList =
          crsSupport.getSupportedCrsList(apiData).stream()
              .map(crs -> crs.equals(OgcCrs.CRS84h) ? OgcCrs.CRS84 : crs)
              .map(EpsgCrs::toUriString)
              .collect(ImmutableList.toImmutableList());
      schemaMap.get(apiHashCode).put("*", new StringSchema()._enum(crsList)._default(defaultCrs));
    }
    return schemaMap.get(apiHashCode).get("*");
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
      // always support both default CRSs
      String defaultCrs =
          apiData
              .getExtension(FeaturesCoreConfiguration.class, collectionId)
              .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
              .map(EpsgCrs::toUriString)
              .orElse(CRS84);
      ImmutableList.Builder<String> crsListBuilder = new ImmutableList.Builder<>();
      List<String> crsList =
          crsSupport
              .getSupportedCrsList(apiData, apiData.getCollections().get(collectionId))
              .stream()
              .flatMap(
                  crs -> Stream.of(crs.toUriString(), crs.toAlternativeUriString().orElse(null)))
              .filter(Objects::nonNull)
              .collect(ImmutableList.toImmutableList());
      crsListBuilder.addAll(crsList);
      if (!crsList.contains(CRS84)) crsListBuilder.add(CRS84);
      schemaMap
          .get(apiHashCode)
          .put(collectionId, new StringSchema()._enum(crsListBuilder.build())._default(defaultCrs));
    }
    return schemaMap.get(apiHashCode).get(collectionId);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FilterConfiguration.class;
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
