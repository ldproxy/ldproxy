/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author zahnen
 */
@Singleton
@AutoBind
public class GeoJsonWriterSkeleton implements GeoJsonWriter {

  @Inject
  public GeoJsonWriterSkeleton() {}

  @Override
  public GeoJsonWriterSkeleton create() {
    return new GeoJsonWriterSkeleton();
  }

  @Override
  public int getSortPriority() {
    return 0;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.encoding().isFeatureCollection()) {

      context.encoding().getJson().writeStartObject();
      context.encoding().getJson().writeStringField("type", "FeatureCollection");
    }

    next.accept(context);

    if (context.encoding().isFeatureCollection()) {
      context.encoding().getJson().writeFieldName("features");
      context.encoding().getJson().writeStartArray();
    }
  }

  @Override
  public void onEnd(EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (context.encoding().isFeatureCollection()) {
      // end of features array
      context.encoding().getJson().writeEndArray();
    }

    // next chain for extensions
    next.accept(context);

    if (context.encoding().isFeatureCollection()) {
      // end of collection object
      context.encoding().getJson().writeEndObject();
    }
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    context.encoding().getJson().writeStartObject();
    context.encoding().getJson().writeStringField("type", "Feature");

    context.encoding().pushBuffer(context.schema().get(), false);

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    // next chain for extensions
    next.accept(context);

    context.encoding().popBuffer();

    // end of feature
    context.encoding().getJson().writeEndObject();

    context.encoding().getJson().flush();
  }

  @Override
  public void onObjectStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    // next chain for extensions
    next.accept(context);

    if (context.schema().map(SchemaBase::isEmbeddedFeature).orElse(false)) {
      context.encoding().getJson().writeStringField("type", "Feature");

      context.encoding().pushBuffer(context.schema().get(), true);
    }
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    // next chain for extensions
    next.accept(context);

    if (context.schema().map(SchemaBase::isEmbeddedFeature).orElse(false)) {
      context.encoding().popBuffer();
    }
  }
}
