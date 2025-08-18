/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.domain;

import static de.ii.xtraplatform.base.domain.util.LambdaWithException.consumerMayThrow;

import com.google.common.collect.ImmutableCollection;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext.Event;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoderDefault;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureEncoderCityJson
    extends FeatureTokenEncoderDefault<EncodingAwareContextCityJson> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEncoderCityJson.class);

  private final ImmutableCollection<CityJsonWriter> featureWriters;
  private final FeatureTransformationContextCityJson transformationContext;
  private final long transformerStart = System.nanoTime();
  private long processingStart;
  private long featureStart;
  private long featureCount;
  private long featuresDuration;

  public FeatureEncoderCityJson(
      FeatureTransformationContextCityJson transformationContext,
      ImmutableCollection<CityJsonWriter> featureWriters) {
    super();
    this.transformationContext = transformationContext;
    this.featureWriters = featureWriters;
  }

  private Consumer<EncodingAwareContextCityJson> executePipeline(
      final Iterator<CityJsonWriter> featureWriterIterator) {
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
  public void onStart(EncodingAwareContextCityJson context) {
    this.processingStart = System.nanoTime();

    if (transformationContext.getOutputStream() instanceof OutputStreamToByteConsumer) {
      ((OutputStreamToByteConsumer) transformationContext.getOutputStream())
          .setByteConsumer(this::push);
    }

    getState().setNumberReturned(context.metadata().getNumberReturned());
    getState().setNumberMatched(context.metadata().getNumberMatched());
    getState().setEvent(Event.START);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onEnd(EncodingAwareContextCityJson context) {
    getState().setEvent(Event.END);
    executePipeline(featureWriters.iterator()).accept(context);

    try {
      transformationContext.getJson().close();
    } catch (IOException e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Error while closing the JSON generator for CityJSON.", e);
      }
      if (LOGGER.isDebugEnabled(LogContext.MARKER.STACKTRACE)) {
        LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace:", e);
      }
    }

    if (LOGGER.isDebugEnabled()) {
      long transformerDuration = toMilliseconds(System.nanoTime() - transformerStart);
      long processingDuration = toMilliseconds(System.nanoTime() - processingStart);
      String text =
          String.format(
              "Collection %s. CityJSON features returned: %d, total duration: %dms, processing: %dms, feature processing: %dms, average feature processing: %dms.",
              context.encoding().getCollectionId(),
              featureCount,
              transformerDuration,
              processingDuration,
              toMilliseconds(featuresDuration),
              featureCount == 0 ? 0 : toMilliseconds(featuresDuration / featureCount));
      if (processingDuration > 200) {
        LOGGER.debug(text);
      } else if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(text);
      }
    }
  }

  @Override
  public void onFeatureStart(EncodingAwareContextCityJson context) {
    this.featureStart = System.nanoTime();

    getState().setEvent(Event.FEATURE_START);
    executePipeline(featureWriters.iterator()).accept(context);

    getState().setCurrentFeatureType(Optional.empty());
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextCityJson context) {
    getState().setCurrentFeatureType(Optional.empty());
    getState().setEvent(Event.FEATURE_END);
    executePipeline(featureWriters.iterator()).accept(context);

    // write vertices in case of text sequences
    executePipeline(featureWriters.iterator()).accept(context);

    this.featuresDuration += System.nanoTime() - featureStart;
    this.featureCount++;
  }

  @Override
  public void onObjectStart(EncodingAwareContextCityJson context) {
    getState().setEvent(Event.OBJECT_START);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onObjectEnd(EncodingAwareContextCityJson context) {
    getState().setEvent(Event.OBJECT_END);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onArrayStart(EncodingAwareContextCityJson context) {
    getState().setEvent(Event.ARRAY_START);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onArrayEnd(EncodingAwareContextCityJson context) {
    getState().setEvent(Event.ARRAY_END);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onGeometry(EncodingAwareContextCityJson context) {
    getState().setEvent(Event.GEOMETRY);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public void onValue(EncodingAwareContextCityJson context) {
    getState().setEvent(Event.PROPERTY);
    executePipeline(featureWriters.iterator()).accept(context);
  }

  @Override
  public Class<? extends EncodingAwareContextCityJson> getContextInterface() {
    return EncodingAwareContextCityJson.class;
  }

  @Override
  public EncodingAwareContextCityJson createContext() {
    return ModifiableEncodingAwareContextCityJson.create().setEncoding(transformationContext);
  }

  private long toMilliseconds(long nanoseconds) {
    return nanoseconds / 1_000_000;
  }

  private @NotNull ModifiableStateCityJson getState() {
    return Objects.requireNonNull(transformationContext.getState());
  }
}
