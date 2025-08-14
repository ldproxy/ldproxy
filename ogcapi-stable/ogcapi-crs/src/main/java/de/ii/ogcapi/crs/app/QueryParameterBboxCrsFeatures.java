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
 * @title bbox-crs
 * @endpoints Features
 * @langEn The coordinate reference system of the `bbox` parameter. Default is WGS84
 *     longitude/latitude.
 * @langDe Das Koordinatenreferenzsystem des Parameters 'bbox'. Default ist WGS84
 *     longitude/latitude.
 */
@Singleton
@AutoBind
public class QueryParameterBboxCrsFeatures extends OgcApiQueryParameterBase
    implements TypedQueryParameter<EpsgCrs> {

  public static final String BBOX = "bbox";
  public static final String BBOX_CRS = "bbox-crs";
  public static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
  public static final String CRS84H = "http://www.opengis.net/def/crs/OGC/0/CRS84h";

  private final CrsSupport crsSupport;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterBboxCrsFeatures(CrsSupport crsSupport, SchemaValidator schemaValidator) {
    this.crsSupport = crsSupport;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId(String collectionId) {
    return BBOX_CRS + "Features_" + collectionId;
  }

  @Override
  public String getName() {
    return BBOX_CRS;
  }

  @Override
  public EpsgCrs parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    FeatureTypeConfigurationOgcApi collectionData =
        optionalCollectionData.orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "The parameter '%s' could not be processed, no collection provided.",
                        getName())));

    EpsgCrs bboxCrs;
    try {
      bboxCrs = Objects.nonNull(value) ? EpsgCrs.fromString(value) : OgcCrs.CRS84;
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("The parameter '%s' is invalid: %s", BBOX_CRS, e.getMessage()), e);
    }
    // CRS84 is always supported
    if (!crsSupport.isSupported(api.getData(), collectionData, bboxCrs)
        && !bboxCrs.equals(OgcCrs.CRS84)) {
      throw new IllegalArgumentException(
          String.format(
              "The parameter '%s' is invalid: the coordinate reference system '%s' is not supported",
              BBOX_CRS, bboxCrs.toUriString()));
    }

    return bboxCrs;
  }

  @Override
  public String getDescription() {
    return "The coordinate reference system of the `bbox` parameter. Default is WGS84 longitude/latitude.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items");
  }

  private final ConcurrentMap<Integer, ConcurrentMap<String, Schema<?>>> schemaMap =
      new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
      // TODO: include 2D (variants) of the CRSs
      // default is currently always CRS84
      String defaultCrs = CRS84 /* TODO support 4 or 6 numbers
            apiData.getExtension(FeaturesCoreConfiguration.class, collectionId)
                .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
                .map(EpsgCrs::toUriString)
                .orElse(CRS84) */;
      List<String> crsList =
          crsSupport
              .getSupportedCrsList(apiData, apiData.getCollections().get(collectionId))
              .stream()
              .map(crs -> crs.equals(OgcCrs.CRS84h) ? OgcCrs.CRS84 : crs)
              .flatMap(
                  crs -> Stream.of(crs.toUriString(), crs.toAlternativeUriString().orElse(null)))
              .filter(Objects::nonNull)
              .collect(ImmutableList.toImmutableList());
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
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return CrsBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return CrsBuildingBlock.SPEC;
  }
}
