/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.geometry.simplification.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
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
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery.Builder;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.kortforsyningen.proj.Units;

/**
 * @title zoom-level
 * @endpoints Features, Feature
 * @langEn All geometries are simplified for the zoom level from the widely used tiling scheme in
 *     web mapping (WebMercatorQuad). The map scale of zoom level 0 is roughly 1:560 million. The
 *     scale doubles for every zoom level (e.g. zoom level 12 is 1:136495).
 * @langDe Alle Geometrien werden für die Zoomstufe aus dem im Webmapping weit verbreiteten
 *     Kachelschema (WebMercatorQuad) vereinfacht. Der Kartenmaßstab der Zoomstufe 0 beträgt etwa
 *     1:560 Millionen. Der Maßstab verdoppelt sich für jede Zoomstufe (z.B. Zoomstufe 12 ist
 *     1:136495).
 */
@Singleton
@AutoBind
public class QueryParameterZoomLevelFeatures extends OgcApiQueryParameterBase
    implements FeatureQueryParameter, TypedQueryParameter<Double>, ConformanceClass {

  private final SchemaValidator schemaValidator;
  private final CrsInfo crsInfo;

  @Inject
  QueryParameterZoomLevelFeatures(SchemaValidator schemaValidator, CrsInfo crsInfo) {
    this.schemaValidator = schemaValidator;
    this.crsInfo = crsInfo;
  }

  @Override
  public int getPriority() {
    // wait for parsed results of crs
    return 2;
  }

  @Override
  public String getName() {
    return "zoom-level";
  }

  @Override
  public Double parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid value for query parameter '%s'. The value must be a number. Found: %s.",
              getName(), value),
          e);
    }
  }

  @Override
  public void applyTo(
      Builder queryBuilder,
      QueryParameterSet parameters,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData) {
    parameters
        .getValue(this)
        .ifPresent(
            level -> {
              double maxAllowableOffset = 559_082_264.028_717 / 4096 / Math.pow(2, level);
              EpsgCrs crs =
                  parameters.getTypedValues().containsKey("crs")
                      ? (EpsgCrs) parameters.getTypedValues().get("crs")
                      : OgcCrs.CRS84;
              if (Units.DEGREE.equals(crsInfo.getUnit(crs))) {
                maxAllowableOffset /= 111_319.5;
              }
              queryBuilder.maxAllowableOffset(maxAllowableOffset);

              if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Zoom level: {}; epsilon: {}", level, maxAllowableOffset);
              }
            });
  }

  @Override
  public String getDescription() {
    return "This option can be used to simplify geometries for the zoom level from the widely used tiling scheme in web mapping (WebMercatorQuad). The map scale of zoom level 0 is roughly 1:560 million. The scale doubles for every zoom level (e.g. zoom level 12 is 1:136495).";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath)
        || "/collections/{collectionId}/items/{featureId}".equals(definitionPath);
  }

  private final Schema<?> schema =
      new NumberSchema()
          .minimum(BigDecimal.valueOf(0))
          .maximum(BigDecimal.valueOf(24))
          .example(12.5);

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
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GeometrySimplificationConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return GeometrySimplificationBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return GeometrySimplificationBuildingBlock.SPEC;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of(
        "http://www.opengis.net/spec/ogcapi-features-7/0.0/conf/zoom-level",
        "http://www.opengis.net/spec/ogcapi-features-7/0.0/conf/zoom-level-features");
  }
}
