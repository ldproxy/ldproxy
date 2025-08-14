/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.routes.domain.RoutingConfiguration;
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
 * @endpoints Routes
 * @langEn The coordinate reference system of the returned features. Default is WGS84
 *     longitude/latitude (with or without ellipsoidal height).
 * @langDe Das Koordinatenreferenzsystem der zurückgegebenen Features. Default ist WGS84
 *     longitude/latitude (mit oder ohne Höhe).
 */
@Singleton
@AutoBind
public class QueryParameterCrsRoutes extends OgcApiQueryParameterBase
    implements FeatureQueryParameter, TypedQueryParameter<EpsgCrs> {

  public static final String CRS = "crs";

  private final CrsSupport crsSupport;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterCrsRoutes(CrsSupport crsSupport, SchemaValidator schemaValidator) {
    this.crsSupport = crsSupport;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId() {
    return CRS + "Routes";
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
      targetCrs = Objects.nonNull(value) ? EpsgCrs.fromString(value) : OgcCrs.CRS84;
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("The parameter '%s' is invalid: %s", getName(), e.getMessage()), e);
    }
    if (!crsSupport.isSupported(api.getData(), targetCrs)) {
      throw new IllegalArgumentException(
          String.format(
              "The parameter '%s' is invalid: the crs '%s' is not supported.",
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
    return "The coordinate reference system of the response geometries. The default is WGS 84 longitude/latitude with optional height.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/routes");
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) {
    return computeIfAbsent(
        this.getClass().getCanonicalName() + apiData.hashCode() + definitionPath + method.name(),
        () ->
            isEnabledForApi(apiData) && method == HttpMethods.POST && matchesPath(definitionPath));
  }

  private final ConcurrentMap<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) {
      List<String> crsList =
          crsSupport.getSupportedCrsList(apiData).stream()
              .flatMap(
                  crs -> Stream.of(crs.toUriString(), crs.toAlternativeUriString().orElse(null)))
              .filter(Objects::nonNull)
              .collect(ImmutableList.toImmutableList());
      String defaultCrs =
          apiData
              .getExtension(RoutingConfiguration.class)
              .map(RoutingConfiguration::getDefaultCrs)
              .map(
                  crs ->
                      crs.equals(FeaturesCoreConfiguration.DefaultCrs.CRS84)
                          ? OgcCrs.CRS84_URI
                          : OgcCrs.CRS84h_URI)
              .orElse(OgcCrs.CRS84_URI);
      schemaMap.put(apiHashCode, new StringSchema()._enum(crsList)._default(defaultCrs));
    }
    return schemaMap.get(apiHashCode);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return RoutingConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }
}
