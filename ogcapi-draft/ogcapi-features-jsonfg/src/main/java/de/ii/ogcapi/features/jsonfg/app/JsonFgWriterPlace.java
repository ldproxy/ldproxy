/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.FeatureState;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.GeometryState;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriterGeometryBase;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgGeometryType;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import java.util.Set;
import java.util.stream.Collectors;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class JsonFgWriterPlace extends GeoJsonWriterGeometryBase {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonFgWriterPlace.class);

  public static String JSON_KEY = "place";

  @Inject
  JsonFgWriterPlace() {}

  @Override
  public JsonFgWriterPlace create() {
    return new JsonFgWriterPlace();
  }

  @Override
  public int getSortPriority() {
    return 35;
  }

  @Override
  protected String geomPropertyName() {
    return JSON_KEY;
  }

  /* FIXME
  Map<String, Boolean> collectionMap;
  boolean isEnabled;
  private boolean geometryOpen;
  private boolean additionalArray;
  private boolean hasPlaceGeometry;
  private boolean hasSecondaryGeometry;
  private boolean suppressPlace;
  private TokenBuffer json;
  private boolean skippingUnsupportedGeometry;
  private Set<SimpleFeatureGeometry> unsupportedGeometries;

  @Override
  public int getSortPriority() {
    return 140;
  }

  private void reset(EncodingAwareContextGeoJson context) {
    this.geometryOpen = false;
    this.hasPlaceGeometry = false;
    this.additionalArray = false;
    this.skippingUnsupportedGeometry = false;
    this.json = new TokenBuffer(new ObjectMapper(), false);
    if (context.encoding().getPrettify()) {
      json.useDefaultPrettyPrinter();
    }
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    collectionMap = getCollectionMap(context.encoding());
    unsupportedGeometries = new HashSet<>();

    next.accept(context);
  */

  @Override
  protected boolean isEnabled(EncodingAwareContextGeoJson context) {
    return writeJsonFgExtensions
        && (geometryIsNotSimpleFeature(context.encoding().getBuffer().get())
            || targetCrsIsNotWgs84(context.encoding().getTargetCrs()));
  }

  private static boolean geometryIsNotSimpleFeature(FeatureState featureState) {
    return !featureState.primaryGeometryProperty.stream()
        .findFirst()
        .map(SchemaBase::isSimpleFeatureGeometry)
        .orElse(false);
  }

  /* FIXME
  @Override
  public void onObjectStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled
        && !suppressPlace
        && context.schema().filter(SchemaBase::isSpatial).isPresent()
        && context.geometryType().isPresent()
        && isPlaceGeometry(context.schema().get())) {

      SimpleFeatureGeometry sfGeometryType = context.geometryType().get();
      JsonFgGeometryType jsonFgGeometryType =
          JsonFgGeometryType.forSimpleFeatureType(
              context.geometryType().get(),
              context
                  .schema()
                  .flatMap(s -> s.getConstraints().flatMap(SchemaConstraints::getComposite))
                  .orElse(false),
              context
                  .schema()
                  .flatMap(s -> s.getConstraints().flatMap(SchemaConstraints::getClosed))
                  .orElse(false));
      if (!jsonFgGeometryType.isSupported()) {
        if (LOGGER.isWarnEnabled() && !this.unsupportedGeometries.contains(sfGeometryType)) {
          LOGGER.warn(
              "Ignoring one or more JSON-FG geometries in 'place' since an unsupported geometry type was provided: '{}'. Writing a null geometry.",
              sfGeometryType);
          this.unsupportedGeometries.add(sfGeometryType);
        }
        json.writeFieldName(JSON_KEY);
        json.writeNull();
        this.skippingUnsupportedGeometry = true;
      } else {
        json.writeFieldName(JSON_KEY);
        json.writeStartObject();
        json.writeStringField("type", jsonFgGeometryType.toString());
        json.writeFieldName("coordinates");

        if (jsonFgGeometryType.equals(JsonFgGeometryType.POLYHEDRON)) {
          json.writeStartArray();
          additionalArray = true;
        }

        geometryOpen = true;
      }

      hasPlaceGeometry = true;
    }

    next.accept(context);
  }

  @Override
  public void onArrayStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (geometryOpen) {
      json.writeStartArray();
    }

    next.accept(context);
  }

  @Override
  public void onArrayEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (geometryOpen) {
      json.writeEndArray();
    }

    next.accept(context);
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.schema().filter(SchemaBase::isSpatial).isPresent() && geometryOpen) {

      this.geometryOpen = false;

      if (additionalArray) {
        additionalArray = false;
        json.writeEndArray();
      }

      // close geometry object
      json.writeEndObject();
    }

    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (geometryOpen) {
      json.writeRawValue(context.value());
    }

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (isEnabled) {
      if (!hasPlaceGeometry) {
        // write null geometry if none was written for this feature
        json.writeFieldName(JSON_KEY);
        json.writeNull();
      }
      json.serialize(context.encoding().getJson());
      json.flush();
    }

    next.accept(context);
  }

  private Map<String, Boolean> getCollectionMap(
      FeatureTransformationContextGeoJson transformationContext) {
    ImmutableMap.Builder<String, Boolean> builder = ImmutableMap.builder();
    transformationContext
        .getFeatureSchemas()
        .keySet()
        .forEach(
            collectionId ->
                transformationContext
                    .getApiData()
                    .getExtension(JsonFgConfiguration.class, collectionId)
                    .ifPresentOrElse(
                        cfg -> {
                          boolean enabled =
                              cfg.isEnabled()
                                  && (cfg.getIncludeInGeoJson()
                                          .contains(JsonFgConfiguration.OPTION.place)
                                      || transformationContext
                                          .getMediaType()
                                          .equals(FeaturesFormatJsonFg.MEDIA_TYPE)
                                      || transformationContext
                                          .getMediaType()
                                          .equals(FeaturesFormatJsonFgCompatibility.MEDIA_TYPE));
                          builder.put(collectionId, enabled);
                        },
                        () -> builder.put(collectionId, false)));
    return builder.build();
  }

  private boolean hasSecondaryGeometry(FeatureSchema schema) {
    return schema.getProperties().stream()
        .filter(SchemaBase::isSecondaryGeometry)
  */

  private static boolean targetCrsIsNotWgs84(EpsgCrs targetCrs) {
    return !(targetCrs.equals(OgcCrs.CRS84) || targetCrs.equals(OgcCrs.CRS84h));
  }

  @Override
  protected GeometryState geometryState() {
    return GeometryState.IN_PLACE;
  }

  @Override
  protected Set<FeatureSchema> getProperty(
      FeatureSchema schema, FeatureTransformationContext transformationContext) {
    if (!writeJsonFgExtensions) {
      return Set.of();
    }

    if (schema.getAllNestedProperties().stream()
            .filter(SchemaBase::isPrimaryGeometry)
            .allMatch(SchemaBase::isSimpleFeatureGeometry)
        && (transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return Set.of();
    }

    return schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isPrimaryGeometry)
        .collect(Collectors.toSet());
  }

  @Override
  protected Set<FeatureSchema> getEmbeddedFeatureProperty(
      FeatureSchema schema, FeatureTransformationContext transformationContext) {
    if (!writeJsonFgExtensions) {
      return Set.of();
    }

    if (schema.getEmbeddedPrimaryGeometry().map(SchemaBase::isSimpleFeatureGeometry).orElse(false)
        && (transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return Set.of();
    }

    return schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isEmbeddedPrimaryGeometry)
        .collect(Collectors.toSet());
  }

  @Override
  protected String getGeometryType(EncodingAwareContextGeoJson context) {
    return JsonFgGeometryType.forSimpleFeatureType(
            context.geometryType().get(),
            context
                .schema()
                .flatMap(s -> s.getConstraints().flatMap(SchemaConstraints::getComposite))
                .orElse(false),
            context
                .schema()
                .flatMap(s -> s.getConstraints().flatMap(SchemaConstraints::getClosed))
                .orElse(false))
        .toString();
  }
}
