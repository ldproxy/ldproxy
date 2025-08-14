/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.geojson.app.JsonGeneratorDebug;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.features.json.domain.GeoJsonGeometryType;
import de.ii.xtraplatform.geometries.domain.ImmutableCoordinatesTransformer;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zahnen
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public abstract class FeatureTransformationContextGeoJson implements FeatureTransformationContext {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FeatureTransformationContextGeoJson.class);

  public enum BufferingState {
    BUFFERING,
    PAUSED,
    FLUSHED
  }

  public enum GeometryState {
    IN_GEOMETRY,
    IN_PLACE,
    IN_OTHER_GEOMETRY,
    NOT_IN_GEOMETRY
  }

  @Override
  @Value.Default
  public ModifiableStateGeoJson getState() {
    return ModifiableStateGeoJson.create();
  }

  public abstract GeoJsonConfiguration getGeoJsonConfig();

  public abstract Map<String, Object> getExtensions();

  @Value.Default
  protected JsonGenerator getJsonGenerator() {
    JsonGenerator json = null;
    try {
      json = new JsonFactory().createGenerator(getOutputStream());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    json.setCodec(new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL));
    if (getPrettify()) {
      json.useDefaultPrettyPrinter();
    }
    if (getDebug()) {
      // Zum JSON debuggen hier einschalten.
      json = new JsonGeneratorDebug(json);
    }

    return json;
  }

  public class FeatureState {
    public TokenBuffer tokenBuffer;
    public BufferingState bufferingState;
    // TODO remove schema
    public FeatureSchema schema;

    public boolean hasProperties;
    public boolean hasGeometry;
    public boolean hasTime;
    public GeometryState geometryState;
    public boolean isPolyhedron;
    // Note that there can be multiple matching properties in embedded features in case of concat
    // or coalesce, so a set is used
    public Set<FeatureSchema> primaryGeometryProperty;
    public Set<FeatureSchema> secondaryGeometryProperty;
    public Set<FeatureSchema> instantProperty;
    public Set<FeatureSchema> intervalStartProperty;
    public Set<FeatureSchema> intervalEndProperty;
    public String currentIntervalStart;
    public String currentIntervalEnd;
    public String currentInstant;
    public String typeTemplate;

    FeatureState(FeatureSchema schema, boolean embeddedFeature) {
      tokenBuffer = createJsonBuffer();
      bufferingState = BufferingState.BUFFERING;

      hasProperties = false;
      hasGeometry = false;
      hasTime = false;
      geometryState = GeometryState.NOT_IN_GEOMETRY;
      isPolyhedron = false;
      currentInstant = null;
      currentIntervalStart = null;
      currentIntervalEnd = null;
      typeTemplate = null;
      this.schema = schema;
      if (embeddedFeature) {
        primaryGeometryProperty =
            schema.getAllNestedProperties().stream()
                .filter(SchemaBase::isEmbeddedPrimaryGeometry)
                .collect(Collectors.toSet());
        secondaryGeometryProperty =
            schema.getAllNestedProperties().stream()
                .filter(SchemaBase::isEmbeddedSecondaryGeometry)
                .collect(Collectors.toSet());
        instantProperty =
            schema.getAllNestedProperties().stream()
                .filter(SchemaBase::isEmbeddedPrimaryInstant)
                .collect(Collectors.toSet());
        intervalStartProperty =
            schema.getAllNestedProperties().stream()
                .filter(SchemaBase::isEmbeddedPrimaryIntervalStart)
                .collect(Collectors.toSet());
        intervalEndProperty =
            schema.getAllNestedProperties().stream()
                .filter(SchemaBase::isEmbeddedPrimaryIntervalEnd)
                .collect(Collectors.toSet());
      } else {
        primaryGeometryProperty = schema.getPrimaryGeometry().stream().collect(Collectors.toSet());
        secondaryGeometryProperty =
            schema.getSecondaryGeometry().stream().collect(Collectors.toSet());
        instantProperty = schema.getPrimaryInstant().stream().collect(Collectors.toSet());
        intervalStartProperty =
            schema.getPrimaryInterval().map(Tuple::first).filter(Objects::nonNull).stream()
                .collect(Collectors.toSet());
        intervalEndProperty =
            schema.getPrimaryInterval().map(Tuple::second).filter(Objects::nonNull).stream()
                .collect(Collectors.toSet());
      }
    }

    public boolean timeIsComplete() {
      return (instantProperty.isEmpty() || currentInstant != null)
          && (intervalStartProperty.isEmpty() || currentIntervalStart != null)
          && (intervalEndProperty.isEmpty() || currentIntervalEnd != null);
    }

    public void setTimeValue(FeatureSchema schema, String value) {
      if (instantProperty.stream().anyMatch(schema::equals)) {
        currentInstant = value;
      } else if (intervalStartProperty.stream().anyMatch(schema::equals)) {
        currentIntervalStart = value;
      } else if (intervalEndProperty.stream().anyMatch(schema::equals)) {
        currentIntervalEnd = value;
      }
    }
  }

  // TODO: to state
  private final Deque<FeatureState> featuresStates = new ArrayDeque<>();

  private TokenBuffer createJsonBuffer() {
    TokenBuffer json = new TokenBuffer(new ObjectMapper(), false);

    if (getPrettify()) {
      json.useDefaultPrettyPrinter();
    }
    return json;
  }

  public Optional<FeatureState> getBuffer() {
    return featuresStates.isEmpty() ? Optional.empty() : Optional.of(featuresStates.getLast());
  }

  private Optional<FeatureState> getBufferingBuffer() {
    Iterator<FeatureState> it = featuresStates.descendingIterator();
    while (it.hasNext()) {
      FeatureState buffer = it.next();
      if (buffer.bufferingState == BufferingState.BUFFERING) {
        return Optional.of(buffer);
      }
    }
    return Optional.empty();
  }

  private Optional<FeatureState> getNextBuffer(FeatureState currentBuffer) {
    Iterator<FeatureState> it = featuresStates.descendingIterator();
    boolean found = false;
    while (it.hasNext()) {
      FeatureState buffer = it.next();
      if (found && buffer.bufferingState != BufferingState.FLUSHED) {
        return Optional.of(buffer);
      }
      found = found || buffer == currentBuffer;
    }
    return Optional.empty();
  }

  public JsonGenerator getJson() {
    return getBufferingBuffer()
        .map(buffer -> (JsonGenerator) buffer.tokenBuffer)
        .orElse(getJsonGenerator());
  }

  public final void continueBuffering() throws IOException {
    FeatureState buffer =
        getBuffer()
            .orElseThrow(
                () -> new IllegalStateException("Cannot restart buffering, no buffer available."));
    if (buffer.bufferingState == BufferingState.PAUSED) {
      buffer.bufferingState = BufferingState.BUFFERING;
    }
  }

  public final void pauseBuffering() throws IOException {
    FeatureState buffer =
        getBuffer()
            .orElseThrow(
                () -> new IllegalStateException("Cannot stop buffering, no buffer available."));
    if (buffer.bufferingState == BufferingState.BUFFERING) {
      buffer.bufferingState = BufferingState.PAUSED;
    }
  }

  public final void pushBuffer(FeatureSchema schema, boolean embeddedFeature) throws IOException {
    featuresStates.addLast(new FeatureState(schema, embeddedFeature));
  }

  public final void popBuffer() throws IOException {
    FeatureState buffer = this.featuresStates.pollLast();
    if (buffer != null && !buffer.bufferingState.equals(BufferingState.FLUSHED)) {
      // flush the current buffer to the next one, if necessary
      buffer.tokenBuffer.close();
      buffer.tokenBuffer.serialize(
          getBuffer()
              .map(buffer1 -> (JsonGenerator) buffer1.tokenBuffer)
              .orElse(getJsonGenerator()));
      buffer.tokenBuffer.flush();
    }
  }

  public final boolean inEmbeddedFeature() {
    return !featuresStates.isEmpty() && featuresStates.peekLast().schema.isEmbeddedFeature();
  }

  @Value.Modifiable
  public abstract static class StateGeoJson extends State {

    public abstract Optional<GeoJsonGeometryType> getCurrentGeometryType();

    public abstract Optional<ImmutableCoordinatesTransformer.Builder> getCoordinatesWriterBuilder();

    @Value.Default
    public int getCurrentGeometryNestingChange() {
      return 0;
    }

    @Value.Default
    public boolean isBuffering() {
      return false;
    }

    @Value.Default
    public boolean hasMore() {
      return false;
    }

    @Value.Default
    public List<Link> getCurrentFeatureLinks() {
      return ImmutableList.of();
    }

    @Value.Default
    public List<Link> getCurrentFeatureCollectionLinks() {
      return ImmutableList.of();
    }

    @Value.Default
    public Set<SimpleFeatureGeometry> getUnsupportedGeometries() {
      return ImmutableSet.of();
    }
  }
}
