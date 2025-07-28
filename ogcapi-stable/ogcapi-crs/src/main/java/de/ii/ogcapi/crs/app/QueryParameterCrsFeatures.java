/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crs.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.crs.domain.CrsConfiguration;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.DefaultCrs;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
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
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery.Builder;
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
 * @title crs
 * @endpoints Features, Feature
 * @langEn The coordinate reference system of the returned features. Default is WGS84
 *     longitude/latitude (with or without ellipsoidal height).
 * @langDe Das Koordinatenreferenzsystem der zurückgegebenen Features. Default ist WGS84
 *     longitude/latitude (mit oder ohne Höhe).
 */
@Singleton
@AutoBind
public class QueryParameterCrsFeatures extends OgcApiQueryParameterBase
    implements ConformanceClass, FeatureQueryParameter, TypedQueryParameter<EpsgCrs> {

  public static final String CRS = "crs";
  public static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
  public static final String CRS84H = "http://www.opengis.net/def/crs/OGC/0/CRS84h";

  private final CrsSupport crsSupport;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterCrsFeatures(CrsSupport crsSupport, SchemaValidator schemaValidator) {
    this.crsSupport = crsSupport;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId(String collectionId) {
    return CRS + "Features_" + collectionId;
  }

  @Override
  public String getName() {
    return CRS;
  }

  @Override
  public EpsgCrs parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    EpsgCrs targetCrs;
    try {
      if (Objects.nonNull(value)) {
        targetCrs = EpsgCrs.fromString(value);
      } else {
        DefaultCrs defaultCrs =
            optionalCollectionData
                .map(cd -> cd.getExtension(FeaturesCoreConfiguration.class))
                .flatMap(cfg -> cfg.map(FeaturesCoreConfiguration::getDefaultCrs))
                .or(
                    () ->
                        api.getData()
                            .getExtension(FeaturesCoreConfiguration.class)
                            .map(FeaturesCoreConfiguration::getDefaultCrs))
                .orElse(DefaultCrs.CRS84);
        targetCrs = (defaultCrs == DefaultCrs.CRS84h) ? OgcCrs.CRS84h : OgcCrs.CRS84;
      }
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("The parameter '%s' is invalid: %s", getName(), e.getMessage()), e);
    }
    boolean crsIsSupported =
        optionalCollectionData
            .map(cd -> crsSupport.isSupported(api.getData(), cd, targetCrs))
            .orElse(crsSupport.isSupported(api.getData(), targetCrs));
    if (!crsIsSupported) {
      throw new IllegalArgumentException(
          String.format(
              "The parameter '%s' is invalid: the crs '%s' is not supported",
              getName(), targetCrs.toUriString()));
    }
    return targetCrs;
  }

  @Override
  public void applyTo(
      Builder queryBuilder,
      QueryParameterSet parameters,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData) {
    parameters.getValue(this).ifPresent(queryBuilder::crs);
  }

  @Override
  public String getDescription() {
    return "The coordinate reference system of the response geometries. Default is WGS84 longitude/latitude (with or without height).";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items")
        || definitionPath.equals("/collections/{collectionId}/items/{featureId}");
  }

  private final ConcurrentMap<Integer, ConcurrentMap<String, Schema<?>>> schemaMap =
      new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
      List<String> crsList =
          crsSupport
              .getSupportedCrsList(apiData, apiData.getCollections().get(collectionId))
              .stream()
              .flatMap(
                  crs -> Stream.of(crs.toUriString(), crs.toAlternativeUriString().orElse(null)))
              .filter(Objects::nonNull)
              .collect(ImmutableList.toImmutableList());
      String defaultCrs =
          apiData
              .getExtension(FeaturesCoreConfiguration.class, collectionId)
              .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
              .map(EpsgCrs::toUriString)
              .orElse(CRS84);
      schemaMap
          .get(apiHashCode)
          .put(collectionId, new StringSchema()._enum(crsList)._default(defaultCrs));
    }
    return schemaMap.get(apiHashCode).get(collectionId);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrsConfiguration.class;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-features-2/1.0/conf/crs");
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return CrsBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return CrsBuildingBlock.SPEC;
  }
}
