/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.cql.domain.Bbox;
import de.ii.xtraplatform.cql.domain.BooleanValue2;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.IsNull;
import de.ii.xtraplatform.cql.domain.Or;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.SIntersects;
import de.ii.xtraplatform.cql.domain.SpatialLiteral;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.ImmutableBoundingBox;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureInfo;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.measure.Unit;
import org.kortforsyningen.proj.Units;

/**
 * @title bbox
 * @endpoints Features
 * @langEn Select only features that have a primary geometry that intersects the bounding box.
 * @langDe Es werden nur Features ausgewählt, deren primäre Geometrie die Begrenzungsgeometrie
 *     schneidet.
 */
@Singleton
@AutoBind
public class QueryParameterBbox extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Cql2Expression>, FeatureQueryParameter {

  private static final Splitter ARRAY_SPLITTER = Splitter.on(',').trimResults();
  private static final double BUFFER_DEGREE = 0.00001;
  private static final double BUFFER_METRE = 10.0;

  private final Schema<?> baseSchema;
  private final SchemaValidator schemaValidator;
  private final FeaturesCoreProviders providers;
  private final CrsInfo crsInfo;
  private final Cql cql;
  private final CrsTransformerFactory crsTransformerFactory;

  @Inject
  public QueryParameterBbox(
      SchemaValidator schemaValidator,
      FeaturesCoreProviders providers,
      CrsInfo crsInfo,
      Cql cql,
      CrsTransformerFactory crsTransformerFactory) {
    this.schemaValidator = schemaValidator;
    this.providers = providers;
    this.crsInfo = crsInfo;
    this.cql = cql;
    this.crsTransformerFactory = crsTransformerFactory;
    // TODO support 6 coordinates (note: maxItems was originally set to 4 for now, but the CITE
    //      tests require maxItems=6)
    this.baseSchema =
        new ArraySchema()
            .items(new NumberSchema().format("double"))
            .oneOf(
                ImmutableList.of(
                    new Schema<>().minItems(4).maxItems(4),
                    new Schema<>().minItems(6).maxItems(6)));
  }

  @Override
  public String getName() {
    return "bbox";
  }

  @Override
  public int getPriority() {
    // wait for parsed results of bbox-crs
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

    List<String> values = ARRAY_SPLITTER.splitToList(value);
    if (values.size() != 4) {
      throw new IllegalArgumentException(
          String.format(
              "The parameter 'bbox' is invalid: it must have exactly four values, found %d.",
              values.size()));
    }

    EpsgCrs bboxCrs =
        typedValues.containsKey("bbox-crs") ? (EpsgCrs) typedValues.get("bbox-crs") : OgcCrs.CRS84;

    List<Double> bboxCoordinates;
    try {
      bboxCoordinates = values.stream().map(Double::valueOf).collect(Collectors.toList());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format(
              "The parameter 'bbox' is invalid: the coordinates are not valid numbers '%s,%s,%s,%s'",
              values.get(0), values.get(1), values.get(2), values.get(3)));
    }

    // We are using the spatial extent of the data to avoid
    // coordinate transformation errors when a bbox parameter
    // is completely outside of the domain of a projected CRS
    // in which the data is stored. Using the minimal bounding
    // box can lead to surprising results in particular with
    // point features and queries in other CRSs where features
    // on the boundary of the spatial extent are suddenly no
    // longer included in the result. For the purpose of the
    // filter, we do not need the minimal bounding rectangle,
    // but we can use a small buffer to avoid those issues.
    double buffer = getBuffer(bboxCrs);
    Optional<BoundingBox> maxSpatialExtent =
        api.getSpatialExtent(collectionData.getId(), bboxCrs)
            .map(
                bbox2 ->
                    new ImmutableBoundingBox.Builder()
                        .xmin(bbox2.getXmin() - buffer)
                        .xmax(bbox2.getXmax() + buffer)
                        .ymin(bbox2.getYmin() - buffer)
                        .ymax(bbox2.getYmax() + buffer)
                        .epsgCrs(bboxCrs)
                        .build());

    if (maxSpatialExtent.isPresent()
        && (bboxCoordinates.get(0) > maxSpatialExtent.get().getXmax()
            || bboxCoordinates.get(1) > maxSpatialExtent.get().getYmax()
            || bboxCoordinates.get(2) < maxSpatialExtent.get().getXmin()
            || bboxCoordinates.get(3) < maxSpatialExtent.get().getYmin())) {
      // bounding box does not overlap with spatial extent of the data, no match;
      // detecting this is also important to avoid errors when converting bbox coordinates
      // that are outside of the range of the native CRS
      return BooleanValue2.of(false);
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
            .getFilterGeometry();
    if (primaryGeometry.isEmpty()) {
      // no spatial property, matches all features
      return BooleanValue2.of(true);
    }

    Property property = Property.of(primaryGeometry.get().getFullPathAsString());

    Bbox bbox =
        Bbox.of(
            bboxCoordinates.get(0),
            bboxCoordinates.get(1),
            bboxCoordinates.get(2),
            bboxCoordinates.get(3),
            bboxCrs);

    Cql2Expression cql2Expression =
        primaryGeometry.map(SchemaBase::isRequired).orElse(false)
                || !providers
                    .getFeatureProvider(api.getData(), collectionData)
                    .filter(provider -> provider instanceof FeatureQueries)
                    .map(provider -> ((FeatureQueries) provider).supportsIsNull())
                    .orElse(false)
            ? SIntersects.of(property, SpatialLiteral.of(bbox))
            : Or.of(SIntersects.of(property, SpatialLiteral.of(bbox)), IsNull.of(property));

    if (collectionData
        .getExtension(FeaturesCoreConfiguration.class)
        .map(FeaturesCoreConfiguration::getValidateCoordinatesInQueries)
        .orElse(false)) {
      cql.checkCoordinates(
          cql2Expression,
          crsTransformerFactory,
          crsInfo,
          bboxCrs,
          providers
              .getFeatureProvider(api.getData(), collectionData)
              .map(FeatureProvider::info)
              .flatMap(FeatureInfo::getCrs)
              .orElse(null));
    }

    return cql2Expression;
  }

  private double getBuffer(EpsgCrs crs) {
    List<Unit<?>> units = crsInfo.getAxisUnits(crs);
    if (!units.isEmpty()) {
      return Units.METRE.equals(units.get(0)) ? BUFFER_METRE : BUFFER_DEGREE;
    }
    // fallback to meters
    return BUFFER_METRE;
  }

  @Override
  public String getDescription() {
    return "Only features that have a geometry that intersects the bounding box are selected. "
        + "The bounding box is provided as four numbers:\n\n"
        + "* Lower left corner, coordinate axis 1 \n"
        + "* Lower left corner, coordinate axis 2 \n"
        + "* Upper right corner, coordinate axis 1 \n"
        + "* Upper right corner, coordinate axis 2 \n"
        + "The coordinate reference system of the values is WGS 84 longitude/latitude (http://www.opengis.net/def/crs/OGC/1.3/CRS84) "
        + "unless a different coordinate reference system is specified in the parameter `bbox-crs`. "
        + "For WGS 84 longitude/latitude the values are in most cases the sequence of minimum longitude, "
        + "minimum latitude, maximum longitude and maximum latitude. "
        + "However, in cases where the box spans the antimeridian the first value (west-most box edge) is larger "
        + "than the third value (east-most box edge).";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items");
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return baseSchema;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return baseSchema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public boolean isFilterParameter() {
    return true;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return FeaturesCoreBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return FeaturesCoreBuildingBlock.SPEC;
  }
}
