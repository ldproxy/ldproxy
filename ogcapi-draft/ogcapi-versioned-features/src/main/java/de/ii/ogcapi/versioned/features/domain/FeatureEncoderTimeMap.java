/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import com.google.common.collect.ImmutableList;
import de.ii.xtraplatform.features.domain.FeatureEventHandler.ModifiableContext;
import de.ii.xtraplatform.features.domain.FeatureObjectEncoder;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaMapping;
import java.time.Instant;
import java.util.Optional;

/**
 * Base for Time Map output encoders. Per-format subclasses ({@code TimeMapJson}, {@code
 * TimeMapHtml}, {@code TimeMapLinkFormat}) only implement {@link #encode(TimeMap)} — the base
 * handles streaming the feature versions out of the query pipeline into per-version {@link
 * FeatureVersionTimeMap} POJOs, accumulating mementos, and tracking the latest start timestamp.
 *
 * <p>The base extends {@link FeatureObjectEncoder} so token-to-POJO assembly is shared with the
 * existing feature encoders ({@code FeatureEncoderGeoJson}, {@code FeatureEncoderHtml}, {@code
 * FeatureEncoderSfFlat} subclasses); only the per-format byte emission stays format-specific.
 */
public abstract class FeatureEncoderTimeMap
    extends FeatureObjectEncoder<PropertyTimeMap, FeatureVersionTimeMap> {

  protected final EncodingContextTimeMap encodingContext;
  private final ImmutableList.Builder<TimeMap.Memento> mementos = ImmutableList.builder();
  private String latestStartValue;

  protected FeatureEncoderTimeMap(EncodingContextTimeMap encodingContext) {
    this.encodingContext = encodingContext;
  }

  @Override
  public FeatureVersionTimeMap createFeature() {
    return ModifiableFeatureVersionTimeMap.create();
  }

  @Override
  public PropertyTimeMap createProperty() {
    return ModifiablePropertyTimeMap.create();
  }

  @Override
  public void onFeature(FeatureVersionTimeMap feature) {
    Optional<String> startValue = feature.getStartValue();
    if (startValue.isEmpty()) {
      return;
    }
    Instant start = feature.getStart().orElseThrow();
    Instant end = feature.getEnd().orElse(null);
    // The href carries the raw start value — a date stays a date; the parsed instant is only
    // used for formatting in the per-format encoders.
    String href = encodingContext.getFeatureHref() + "?datetime=" + startValue.get();
    mementos.add(
        new TimeMap.Memento(
            startValue.get(),
            feature.getEndValue().orElse(null),
            feature.isStartDate(),
            feature.isEndDate(),
            start,
            end,
            href));
    // The query sorts ascending by the start property and starts are unique per feature, so
    // the last version in the stream is the latest one.
    this.latestStartValue = startValue.get();
  }

  @Override
  public void onEnd(ModifiableContext<FeatureSchema, SchemaMapping> context) {
    TimeMap timeMap =
        new TimeMap(
            encodingContext.getCollectionId(),
            encodingContext.getFeatureId(),
            encodingContext.getFeatureHref(),
            encodingContext.getResourceLinks(),
            mementos.build(),
            latestStartValue);
    try {
      encode(timeMap);
    } catch (Exception e) {
      throw new IllegalStateException("Could not encode Time Map: " + e.getMessage(), e);
    }
  }

  /**
   * Emit the encoded representation by calling {@link #push(byte[])} one or more times. Called once
   * at end-of-stream with the fully assembled {@link TimeMap}.
   */
  protected abstract void encode(TimeMap timeMap) throws Exception;
}
