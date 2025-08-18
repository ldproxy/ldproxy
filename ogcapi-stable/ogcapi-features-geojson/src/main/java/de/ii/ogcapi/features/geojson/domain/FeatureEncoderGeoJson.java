/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.domain;

import static de.ii.xtraplatform.base.domain.util.LambdaWithException.consumerMayThrow;

import com.google.common.collect.ImmutableCollection;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext.Event;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoderDefault;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureEncoderGeoJson extends FeatureTokenEncoderDefault<EncodingAwareContextGeoJson> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEncoderGeoJson.class);

  private final ImmutableCollection<GeoJsonWriter> featureWriters;
  private final FeatureTransformationContextGeoJson transformationContext;

  public FeatureEncoderGeoJson(
      FeatureTransformationContextGeoJson transformationContext,
      ImmutableCollection<GeoJsonWriter> featureWriters) {
    this.transformationContext = transformationContext;
    this.featureWriters = featureWriters;
  }

  private Consumer<EncodingAwareContextGeoJson> executePipeline(
      final Iterator<GeoJsonWriter> featureWriterIterator) {
    return consumerMayThrow(
        nextTransformationContext -> {
          if (featureWriterIterator.hasNext()) {
            featureWriterIterator
                .next()
                .onEvent(nextTransformationContext, this.executePipeline(featureWriterIterator));
          }
        });
  }

  @Override
  public void onStart(EncodingAwareContextGeoJson context) {
    // TODO: more elegant solution
    if (transformationContext.getOutputStream() instanceof OutputStreamToByteConsumer) {
      ((OutputStreamToByteConsumer) transformationContext.getOutputStream())
          .setByteConsumer(this::push);
    }

    transformationContext.getState().setNumberReturned(context.metadata().getNumberReturned());
    transformationContext.getState().setNumberMatched(context.metadata().getNumberMatched());

    transformationContext.getState().setEvent(FeatureTransformationContext.Event.START);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onEnd(EncodingAwareContextGeoJson context) {
    transformationContext.getState().setEvent(FeatureTransformationContext.Event.END);
    executePipeline(featureWriters.iterator()).accept(context);

    try {
      transformationContext.getJson().close();
    } catch (IOException e) {
      if (LOGGER.isDebugEnabled(LogContext.MARKER.STACKTRACE)) {
        LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace: ", e);
      }
    }
  }

  @Override
  public void onFeatureStart(EncodingAwareContextGeoJson context) {
    // TODO: schema
    // transformationContext.getState()
    //    .setCurrentFeatureType(Optional.ofNullable(featureType));

    transformationContext.getState().setEvent(FeatureTransformationContext.Event.FEATURE_START);
    executePipeline(featureWriters.iterator()).accept(context);

    transformationContext.getState().setCurrentFeatureType(Optional.empty());
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextGeoJson context) {
    transformationContext.getState().setCurrentFeatureType(Optional.empty());
    transformationContext.getState().setEvent(FeatureTransformationContext.Event.FEATURE_END);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onObjectStart(EncodingAwareContextGeoJson context) {
    transformationContext.getState().setEvent(Event.OBJECT_START);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onObjectEnd(EncodingAwareContextGeoJson context) {
    transformationContext.getState().setEvent(Event.OBJECT_END);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onArrayStart(EncodingAwareContextGeoJson context) {
    transformationContext.getState().setEvent(Event.ARRAY_START);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onArrayEnd(EncodingAwareContextGeoJson context) {
    transformationContext.getState().setEvent(Event.ARRAY_END);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onGeometry(EncodingAwareContextGeoJson context) {
    transformationContext.getState().setEvent(Event.GEOMETRY);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onValue(EncodingAwareContextGeoJson context) {
    transformationContext.getState().setEvent(Event.PROPERTY);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public Class<? extends EncodingAwareContextGeoJson> getContextInterface() {
    return EncodingAwareContextGeoJson.class;
  }

  @Override
  public EncodingAwareContextGeoJson createContext() {
    return ModifiableEncodingAwareContextGeoJson.create().setEncoding(transformationContext);
  }
}
