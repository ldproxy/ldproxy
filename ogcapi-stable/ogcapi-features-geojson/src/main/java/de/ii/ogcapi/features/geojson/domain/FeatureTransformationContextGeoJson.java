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
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.geojson.app.JsonGeneratorDebug;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.features.json.domain.GeoJsonGeometryType;
import de.ii.xtraplatform.geometries.domain.ImmutableCoordinatesTransformer;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * @author zahnen
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public abstract class FeatureTransformationContextGeoJson implements FeatureTransformationContext {

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

  class BufferEmbeddedFeature {
    TokenBuffer tokenBuffer;
    boolean isBuffering;

    BufferEmbeddedFeature() {
      this.tokenBuffer = createJsonBuffer();
      this.isBuffering = true;
    }
  }

  // TODO: to state
  private TokenBuffer tokenBuffer;
  private final Deque<BufferEmbeddedFeature> bufferEmbeddedFeature = new ArrayDeque<>();

  protected TokenBuffer getJsonBuffer() {
    return tokenBuffer;
  }

  protected TokenBuffer getJsonBufferEmbeddedFeature() {
    Iterator<BufferEmbeddedFeature> it = bufferEmbeddedFeature.descendingIterator();
    while (it.hasNext()) {
      BufferEmbeddedFeature buffer = it.next();
      if (buffer.isBuffering) {
        return buffer.tokenBuffer;
      }
    }
    return null;
  }

  // @Value.Derived
  private TokenBuffer createJsonBuffer() {
    TokenBuffer json = new TokenBuffer(new ObjectMapper(), false);

    if (getPrettify()) {
      json.useDefaultPrettyPrinter();
    }
    return json;
  }

  public JsonGenerator getJson() {
    return Optional.ofNullable((JsonGenerator) getJsonBufferEmbeddedFeature())
        .orElse(getState().isBuffering() ? getJsonBuffer() : getJsonGenerator());
  }

  public final void startBuffering() throws IOException {
    getJsonGenerator().flush();
    this.tokenBuffer = createJsonBuffer();
    getState().setIsBuffering(true);
  }

  public final void stopBuffering() throws IOException {
    if (getState().isBuffering()) {
      getState().setIsBuffering(false);
      getJsonBuffer().close();
    }
  }

  public final void flushBuffer() throws IOException {
    if (!Objects.isNull(getJsonBuffer())) {
      getJsonBuffer().serialize(getJsonGenerator());
      getJsonBuffer().flush();
    }
  }

  public final void startBufferingEmbeddedFeature() throws IOException {
    if (!getState().isBuffering()) {
      getJsonGenerator().flush();
    }
    this.bufferEmbeddedFeature.addLast(new BufferEmbeddedFeature());
  }

  public final void stopBufferingEmbeddedFeature() throws IOException {
    if (!this.bufferEmbeddedFeature.isEmpty()
        && this.bufferEmbeddedFeature.peekLast().isBuffering) {
      getJsonBufferEmbeddedFeature().close();
      this.bufferEmbeddedFeature.peekLast().isBuffering = false;
    }
  }

  public final void flushBufferEmbeddedFeature() throws IOException {
    BufferEmbeddedFeature buffer = this.bufferEmbeddedFeature.pollLast();
    if (!Objects.isNull(buffer)) {
      buffer.tokenBuffer.serialize(getJson());
      buffer.tokenBuffer.flush();
    }
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
  }
}
