/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public abstract class FeatureTransformationContextCityJson implements FeatureTransformationContext {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FeatureTransformationContextCityJson.class);

  @Override
  @Value.Default
  public ModifiableStateCityJson getState() {
    return ModifiableStateCityJson.create();
  }

  public abstract EpsgCrs getCrs();

  @Value.Default
  public boolean getTextSequences() {
    return false;
  }

  @Value.Default
  public CityJsonConfiguration.Version getVersion() {
    return CityJsonConfiguration.Version.V11;
  }

  @Value.Default
  protected JsonGenerator getJsonGenerator() {
    JsonGenerator json;
    try {
      json = new JsonFactory().createGenerator(getOutputStream());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    json.setCodec(new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL));
    if (getPrettify()) {
      json.useDefaultPrettyPrinter();
    }
    if (getTextSequences()) {
      json.setRootValueSeparator(new SerializedString("\n"));
    }

    return json;
  }

  public JsonGenerator getJson() {
    return getJsonGenerator();
  }

  public final void startAttributes() throws IOException {
    Objects.requireNonNull(getState())
        .getAttributesBuffer()
        .ifPresent(val -> getState().setAttributesBufferBuilding(val));
    TokenBuffer buffer = createJsonBuffer();
    getState().setAttributesBuffer(buffer);
    buffer.writeFieldName("attributes");
    buffer.writeStartObject();
  }

  public final void stopAndFlushAttributes() throws IOException {
    TokenBuffer buffer = Objects.requireNonNull(getState()).getAttributesBuffer().orElse(null);
    if (Objects.nonNull(buffer)) {
      buffer.writeEndObject();
      buffer.serialize(getJson());
      buffer.flush();
      buffer.close();
      getState().setAttributesBuffer(Optional.empty());
    }
    if (getState().getAttributesBufferBuilding().isPresent()) {
      getState().setAttributesBuffer(getState().getAttributesBufferBuilding().get());
      getState().setAttributesBufferBuilding(Optional.empty());
    }
  }

  public final void startAddress() throws IOException {
    Objects.requireNonNull(getState())
        .getAddressBuffer()
        .ifPresent(val -> getState().setAddressBufferBuilding(val));
    TokenBuffer buffer = createJsonBuffer();
    getState().setAddressBuffer(buffer);
    buffer.writeFieldName("address");
    if (!getVersion().equals(CityJsonConfiguration.Version.V10)) {
      buffer.writeStartArray();
    }
    buffer.writeStartObject();
  }

  public final void nextAddress() throws IOException {
    if (!getVersion().equals(CityJsonConfiguration.Version.V10)) {
      TokenBuffer buffer = Objects.requireNonNull(getState()).getAddressBuffer().orElse(null);
      if (Objects.nonNull(buffer)) {
        buffer.writeEndObject();
        buffer.writeStartObject();
      }
    }
  }

  public final void stopAddress() throws IOException {
    TokenBuffer buffer = Objects.requireNonNull(getState()).getAddressBuffer().orElse(null);
    if (Objects.nonNull(buffer)) {
      buffer.writeEndObject();
      if (!getVersion().equals(CityJsonConfiguration.Version.V10)) {
        buffer.writeEndArray();
      }
    }
  }

  public final void flushAddress() throws IOException {
    TokenBuffer buffer = Objects.requireNonNull(getState()).getAddressBuffer().orElse(null);
    if (Objects.nonNull(buffer)) {
      buffer.serialize(getJson());
      buffer.flush();
      buffer.close();
    }
    if (getState().getAddressBufferBuilding().isPresent()) {
      getState().setAddressBuffer(getState().getAddressBufferBuilding().get());
      getState().setAddressBufferBuilding(Optional.empty());
    } else {
      getState().setAddressBuffer(Optional.empty());
    }
  }

  public final boolean buildingPartHasAddress() throws IOException {
    return Objects.requireNonNull(getState()).getAddressBufferBuilding().isPresent();
  }

  public final void startGeometry() throws IOException {
    Objects.requireNonNull(getState())
        .getGeometryBuffer()
        .ifPresent(val -> getState().setGeometryBufferBuilding(val));
    TokenBuffer buffer = createJsonBuffer();
    getState().setGeometryBuffer(buffer);
    buffer.writeFieldName("geometry");
    buffer.writeStartArray();
  }

  public final void stopAndFlushGeometry() throws IOException {
    TokenBuffer buffer = Objects.requireNonNull(getState()).getGeometryBuffer().orElse(null);
    if (Objects.nonNull(buffer)) {
      buffer.writeEndArray();
      buffer.serialize(getJson());
      buffer.flush();
      buffer.close();
      getState().setGeometryBuffer(Optional.empty());
    }
    if (getState().getGeometryBufferBuilding().isPresent()) {
      getState().setGeometryBuffer(getState().getGeometryBufferBuilding().get());
      getState().setGeometryBufferBuilding(Optional.empty());
    }
  }

  public void writeAttributeValue(String value, SchemaBase.Type type) throws IOException {
    if (Objects.requireNonNull(getState()).getAttributesBuffer().isEmpty()) {
      return;
    }

    TokenBuffer buffer = Objects.requireNonNull(getState()).getAttributesBuffer().get();
    switch (type) {
      case BOOLEAN:
        // TODO: normalize in decoder
        buffer.writeBoolean(
            value.equalsIgnoreCase("t") || value.equalsIgnoreCase("true") || value.equals("1"));
        break;
      case INTEGER:
        try {
          buffer.writeNumber(Long.parseLong(value));
        } catch (NumberFormatException e) {
          LOGGER.error("Error writing integer value in CityJSON, writing string. Found: {}", value);
          buffer.writeString(value);
        }
        break;
      case FLOAT:
        try {
          buffer.writeNumber(Double.parseDouble(value));
        } catch (NumberFormatException e) {
          LOGGER.error(
              "Error writing floating point value in CityJSON, writing string. Found: {}", value);
          buffer.writeString(value);
        }
        break;
      default:
        buffer.writeString(value);
    }
  }

  public Optional<Integer> processOrdinate(double value) {
    ModifiableStateCityJson state = Objects.requireNonNull(getState());

    state.addCurrentVertex(value);
    if (getState().getCurrentVertex().size() == 3 && state.getCurrentVertices().isPresent()) {
      long[] transformed = new long[3];
      for (int i = 0; i < 3; i++) {
        transformed[i] =
            Math.round(
                (state.getCurrentVertex().get(i) - state.getCurrentTranslate().get(i))
                    / state.getCurrentScale().get(i));
      }
      state.setCurrentVertex(ImmutableList.of());
      return Optional.of(state.getCurrentVertices().get().addVertex(transformed));
    }
    return Optional.empty();
  }

  public void returnToPreviousSection() {
    StateCityJson.Section previous = Objects.requireNonNull(getState()).previousSection();
    StateCityJson.Section current = getState().inSection();
    LOGGER.trace("State change: {} -> {}", current, previous);
    getState().setPreviousSection(current);
    getState().setInSection(previous);
  }

  public void changeSection(StateCityJson.Section newSection) {
    StateCityJson.Section current = Objects.requireNonNull(getState()).inSection();
    LOGGER.trace("State change: {} -> {}", current, newSection);
    getState().setPreviousSection(current);
    getState().setInSection(newSection);
  }

  private TokenBuffer createJsonBuffer() {
    TokenBuffer json = new TokenBuffer(new ObjectMapper(), false);

    if (getPrettify()) {
      json.useDefaultPrettyPrinter();
    }
    return json;
  }

  @Value.Modifiable
  public abstract static class StateCityJson extends State {

    // Transitions:
    // OUTSIDE -> IN_BUILDING
    // IN_BUILDING -> IN_ADDRESS
    // IN_SURFACES_IGNORE | OUTSIDE
    // IN_ADDRESS -> previous
    // WAITING_FOR_SURFACES -> IN_SURFACES | IN_ADDRESS
    // IN_SURFACES -> IN_BUILDING
    // IN_SURFACES_IGNORE -> IN_BUILDING
    public enum Section {
      OUTSIDE,
      IN_BUILDING,
      IN_ADDRESS,
      WAITING_FOR_SURFACES,
      IN_SURFACES,
      IN_SURFACES_IGNORE
    }

    @Value.Default
    public Section inSection() {
      return Section.OUTSIDE;
    }

    @Value.Default
    public Section previousSection() {
      return Section.OUTSIDE;
    }

    @Value.Default
    public boolean inBuildingPart() {
      return false;
    }

    @Value.Derived
    @Value.Auxiliary
    public boolean atTopLevel() {
      return inSection() == Section.IN_BUILDING || inSection() == Section.WAITING_FOR_SURFACES;
    }

    public abstract Optional<TokenBuffer> getAddressBuffer();

    public abstract Optional<TokenBuffer> getAttributesBuffer();

    public abstract Optional<TokenBuffer> getGeometryBuffer();

    public abstract Optional<String> getCurrentId();

    public abstract Optional<String> getCurrentIdBuildingPart();

    public abstract List<Double> getCurrentVertex();

    public abstract Optional<Vertices> getCurrentVertices();

    public abstract List<Double> getCurrentScale();

    public abstract List<Double> getCurrentTranslate();

    public abstract List<String> getCurrentChildren();

    public abstract Optional<TokenBuffer> getAddressBufferBuilding();

    public abstract Optional<TokenBuffer> getAttributesBufferBuilding();

    public abstract Optional<TokenBuffer> getGeometryBufferBuilding();
  }
}
