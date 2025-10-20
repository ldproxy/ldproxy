/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.fasterxml.jackson.core.JsonGenerator;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonFgWriterConformsTo implements GeoJsonWriter {

  public static String JSON_KEY = "conformsTo";

  public static String URI_CORE = "http://www.opengis.net/spec/json-fg-1/0.3/conf/core";
  public static String URI_POLYHEDRA = "http://www.opengis.net/spec/json-fg-1/0.3/conf/polyhedra";
  public static String URI_CIRCULAR_ARCS =
      "http://www.opengis.net/spec/json-fg-1/0.3/conf/circular-arcs";
  public static String URI_TYPE = "http://www.opengis.net/spec/json-fg-1/0.3/conf/types-schemas";
  public static String URI_PROFILES = "http://www.opengis.net/spec/json-fg-1/0.3/conf/profiles";

  boolean isEnabled;
  boolean has3d;
  boolean hasFeatureType;
  boolean hasCircularArcs;

  @Inject
  JsonFgWriterConformsTo() {}

  @Override
  public JsonFgWriterConformsTo create() {
    return new JsonFgWriterConformsTo();
  }

  @Override
  public int getSortPriority() {
    return 10;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    isEnabled = isEnabled(context.encoding());
    has3d = has3d(context.encoding());
    hasFeatureType = hasFeatureType(context.encoding());
    hasCircularArcs = hasCircularArcs(context.encoding());

    if (isEnabled && context.encoding().isFeatureCollection()) {
      writeConformsTo(context.encoding().getJson());
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled && !context.encoding().isFeatureCollection()) {
      writeConformsTo(context.encoding().getJson());
    }

    // next chain for extensions
    next.accept(context);
  }

  private void writeConformsTo(JsonGenerator json) throws IOException {
    json.writeArrayFieldStart(JSON_KEY);
    json.writeString(URI_CORE);
    json.writeString(URI_PROFILES);
    if (has3d) {
      json.writeString(URI_POLYHEDRA);
    }
    if (hasCircularArcs) {
      json.writeString(URI_CIRCULAR_ARCS);
    }
    if (hasFeatureType) {
      json.writeString(URI_TYPE);
    }
    json.writeEndArray();
  }

  private boolean isEnabled(FeatureTransformationContextGeoJson transformationContext) {
    return writeJsonFgExtensions(transformationContext);
  }

  private boolean has3d(FeatureTransformationContextGeoJson transformationContext) {
    return transformationContext
        .getFeatureSchemas()
        .get(transformationContext.getCollectionId())
        .flatMap(SchemaBase::getPrimaryGeometry)
        .filter(
            s ->
                (s.getGeometryType().map(t -> t.equals(GeometryType.MULTI_POLYGON)).orElse(false)
                        && s.getConstraints()
                            .map(c -> c.isClosed() && c.isComposite())
                            .orElse(false))
                    || (s.getGeometryType()
                            .map(t -> t.equals(GeometryType.POLYHEDRAL_SURFACE))
                            .orElse(false)
                        && s.getConstraints().map(SchemaConstraints::isClosed).orElse(false)))
        .isPresent();
  }

  private boolean hasFeatureType(FeatureTransformationContextGeoJson transformationContext) {
    return transformationContext
        .getApiData()
        .getExtension(JsonFgConfiguration.class, transformationContext.getCollectionId())
        .map(
            cfg ->
                Objects.nonNull(
                    cfg.getEffectiveFeatureType(
                        transformationContext
                            .getFeatureSchemas()
                            .get(transformationContext.getCollectionId()))))
        .orElse(false);
  }

  private boolean hasCircularArcs(FeatureTransformationContextGeoJson transformationContext) {
    return !transformationContext
        .getFeatureSchemas()
        .get(transformationContext.getCollectionId())
        .flatMap(SchemaBase::getPrimaryGeometry)
        .map(SchemaBase::isSimpleFeatureGeometry)
        .orElse(true);
  }
}
