/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import de.ii.xtraplatform.features.domain.FeatureEventHandler.ModifiableContext;
import de.ii.xtraplatform.features.domain.FeaturePathTracker;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenTransformer;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaMapping;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rewrites the id token of each feature to {@code <canonical>.<formattedStart>} per the
 * versioned-features composite-id pattern, when the {@code versions-as-features-unique-ids} profile
 * is active.
 *
 * <p>Pipeline placement: same pre-format slot as {@code FeatureTokenTransformerPropertyLinks} (set
 * in {@code FeatureStreamImpl} via {@link
 * de.ii.xtraplatform.features.domain.FeatureTokenTransformerExtension}), so it sees raw provider
 * values and a downstream format-specific date formatter doesn't get to mutate the captured {@code
 * PRIMARY_INTERVAL_START}.
 *
 * <p>Buffer-and-emit semantics: the id token typically arrives before {@code
 * PRIMARY_INTERVAL_START} in the stream. The transformer holds the id event, propagates everything
 * else (including the start), and re-emits the id with the composite value at {@code onFeatureEnd}.
 * If the start never arrives, the canonical id is emitted unchanged.
 */
public class FeatureTokenTransformerCompositeId extends FeatureTokenTransformer {

  private static final DateTimeFormatter FLEXIBLE_PARSER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd[['T'][' ']HH:mm:ss[.SSS]][X]");

  private final String pattern;
  private final String timestampFormat;

  private String heldIdValue;
  private List<String> heldIdPath;
  private int heldSchemaIndex;
  private String currentStart;
  private boolean currentStartIsDate;

  public FeatureTokenTransformerCompositeId(String pattern, String timestampFormat) {
    this.pattern = pattern;
    this.timestampFormat = timestampFormat;
  }

  @Override
  public void onFeatureStart(ModifiableContext<FeatureSchema, SchemaMapping> context) {
    this.heldIdValue = null;
    this.heldIdPath = null;
    this.heldSchemaIndex = -1;
    this.currentStart = null;
    this.currentStartIsDate = false;
    context.setCanonicalFeatureId(null);
    super.onFeatureStart(context);
  }

  @Override
  public void onValue(ModifiableContext<FeatureSchema, SchemaMapping> context) {
    if (context.schema().isPresent()) {
      FeatureSchema schema = context.schema().get();
      if (schema.isId() && Objects.nonNull(context.value())) {
        this.heldIdValue = context.value();
        this.heldIdPath = new ArrayList<>(context.pathTracker().asList());
        this.heldSchemaIndex = context.schemaIndex();
        return;
      }
      if (schema.isPrimaryIntervalStart() && Objects.nonNull(context.value())) {
        this.currentStart = context.value();
        this.currentStartIsDate = schema.getType() == SchemaBase.Type.DATE;
      }
    }
    super.onValue(context);
  }

  @Override
  public void onFeatureEnd(ModifiableContext<FeatureSchema, SchemaMapping> context) {
    if (Objects.nonNull(heldIdValue)) {
      String emit = heldIdValue;
      if (Objects.nonNull(currentStart)) {
        Instant inst = parseStart(currentStart);
        if (Objects.nonNull(inst)) {
          // For a DATE-typed start the suffix defaults to the compact date format, not a
          // start-of-day timestamp.
          emit =
              CompositeIdFormatter.format(
                  pattern, timestampFormat, heldIdValue, inst, currentStartIsDate);
        }
      }
      // Expose the untransformed id so format encoders that need the canonical (e.g.
      // GML's gml:identifier element) can use it instead of the composite that goes on
      // gml:id / GeoJSON id.
      if (!emit.equals(heldIdValue)) {
        context.setCanonicalFeatureId(heldIdValue);
      }
      FeaturePathTracker tracker = context.pathTracker();
      tracker.track(heldIdPath);
      if (heldSchemaIndex >= 0) {
        context.setSchemaIndex(heldSchemaIndex);
      }
      context.setValue(emit);
      context.setValueType(SchemaBase.Type.STRING);
      super.onValue(context);
    }
    super.onFeatureEnd(context);
  }

  private static Instant parseStart(String value) {
    try {
      TemporalAccessor ta =
          FLEXIBLE_PARSER.parseBest(
              value, OffsetDateTime::from, LocalDateTime::from, LocalDate::from);
      if (ta instanceof OffsetDateTime) {
        return ((OffsetDateTime) ta).toInstant();
      } else if (ta instanceof LocalDateTime) {
        return ((LocalDateTime) ta).atZone(ZoneId.of("UTC")).toInstant();
      } else if (ta instanceof LocalDate) {
        return ((LocalDate) ta).atStartOfDay(ZoneOffset.UTC).toInstant();
      }
    } catch (Throwable ignore) {
      // fall through
    }
    return null;
  }
}
