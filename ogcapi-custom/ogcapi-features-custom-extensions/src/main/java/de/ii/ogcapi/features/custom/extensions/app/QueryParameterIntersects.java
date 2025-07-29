/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.custom.extensions.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.custom.extensions.domain.FeaturesExtensionsConfiguration;
import de.ii.ogcapi.features.custom.extensions.domain.GeometryHelperWKT;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.cql.domain.BooleanValue2;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql.Format;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.transcode.wktwkb.GeometryDecoderWkt;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.web.domain.HttpClient;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title intersects
 * @endpoints Features
 * @langEn A Well Known Text representation of a geometry as defined in Simple Feature Access - Part
 *     1: Common Architecture or a URI that returns a GeoJSON feature with a geometry. Only features
 *     are returned that intersect the geometry.
 * @langDe Eine Well-Known-Text-Geometrie gemäß der Definition in Simple Feature Access - Part 1:
 *     Common Architecture oder eine URI, die ein GeoJSON-Feature mit einer Geometrie zurückgibt. Es
 *     werden nur Features zurückgegeben, die die Geometrie schneiden.
 */
@Singleton
@AutoBind
public class QueryParameterIntersects extends OgcApiQueryParameterBase
    implements FeatureQueryParameter, TypedQueryParameter<Cql2Expression> {

  private final FeaturesCoreProviders providers;
  private final GeometryHelperWKT geometryHelper;
  private final GeometryDecoderWkt wktDecoder;
  private final HttpClient httpClient;
  private final SchemaValidator schemaValidator;
  private final Cql cql;

  @Inject
  public QueryParameterIntersects(
      FeaturesCoreProviders providers,
      GeometryHelperWKT geometryHelper,
      Http http,
      SchemaValidator schemaValidator,
      Cql cql) {
    this.providers = providers;
    this.geometryHelper = geometryHelper;
    this.wktDecoder = new GeometryDecoderWkt();
    this.httpClient = http.getDefaultClient();
    this.schemaValidator = schemaValidator;
    this.cql = cql;
  }

  @Override
  public String getId() {
    return "intersects";
  }

  @Override
  public String getName() {
    return "intersects";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items");
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

    String wkt;
    if (value.startsWith("http")) {
      try {
        wkt = getGeometry(value);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            String.format(
                "HTTP URL '%s' in parameter 'intersects' must be a GeoJSON feature with a geometry. Failure to convert to a geometry: %s",
                value, e.getMessage()),
            e);
      }
      validateWkt(wkt)
          .ifPresent(
              error -> {
                throw new IllegalStateException(
                    String.format(
                        "Response to HTTP URL '%s' in parameter 'intersects' cannot be converted to a WKT geometry: %s",
                        value, error));
              });
    } else {
      wkt = value;
    }

    Optional<FeatureSchema> primaryGeometry =
        providers
            .getQueryablesSchema(api.getData(), collectionData)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "The parameter '%s' could not be processed, no feature schema provided.",
                            getName())))
            .getPrimaryGeometry();
    if (primaryGeometry.isEmpty()) {
      // no spatial property, matches all features
      return BooleanValue2.of(true);
    }

    String property = primaryGeometry.get().getFullPathAsString();
    String isNull =
        primaryGeometry.map(SchemaBase::isRequired).orElse(false)
            ? ""
            : String.format(" OR \"%s\" IS NULL", property);
    return cql.read(String.format("S_INTERSECTS(\"%s\",%s)%s", property, wkt, isNull), Format.TEXT);
  }

  @Override
  public boolean isFilterParameter() {
    return true;
  }

  @Override
  public String getDescription() {
    return "A Well Known Text representation of a geometry as defined in Simple Feature Access - Part 1: Common Architecture "
        + "or a URI that returns a GeoJSON feature with a geometry. Only features are returned that intersect the geometry.";
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return new StringSchema()
        .pattern(
            "^(POINT|MULTIPOINT|LINESTRING|MULTILINESTRING|POLYGON|MULTIPOLYGON|GEOMETRYCOLLECTION|http(?:s)?://).*$");
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Optional<String> validateOther(
      OgcApiDataV2 apiData, Optional<String> collectionId, List<String> values) {
    if (values.size() != 1)
      return Optional.of(
          "One value for parameter 'intersects' expected. Found " + values.size() + " values.");
    if (values.get(0).startsWith("http")) return Optional.empty();

    return validateWkt(values.get(0));
  }

  private Optional<String> validateWkt(String wkt) {
    try {
      Geometry<?> geom = new GeometryDecoderWkt().decodeSimpleFeature(wkt, Optional.empty());
      if (geom == null) {
        return Optional.of(
            String.format(
                "The WKT geometry '%s' results in no geometry. Please check the input.", wkt));
      }
      if (geom.isEmpty()) {
        return Optional.of(
            String.format(
                "The WKT geometry '%s' results in an empty geometry. Please check the input.",
                wkt));
      }
    } catch (IOException | IllegalStateException e) {
      return Optional.of(e.getMessage());
    }
    return Optional.empty();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && apiData
            .getExtension(FeaturesExtensionsConfiguration.class, collectionId)
            .map(FeaturesExtensionsConfiguration::getIntersectsParameter)
            .orElse(false);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesExtensionsConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DEPRECATED);
  }

  private String getGeometry(String coordRef) {
    InputStream response = httpClient.getAsInputStream(coordRef);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode jsonNode = null;
    try {
      jsonNode = mapper.readTree(response);
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format("Could not parse GeoJSON geometry object: %s", e.getMessage()), e);
    }

    return geometryHelper.convertGeoJsonToWkt(jsonNode);
  }
}
