/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.fasterxml.jackson.core.JsonGenerator;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.FeatureState;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import de.ii.xtraplatform.geometries.domain.transcode.json.GeometryEncoderJson;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author zahnen
 */
@Singleton
@AutoBind
public class GeoJsonWriterProperties implements GeoJsonWriter {

  @Inject
  public GeoJsonWriterProperties() {}

  @Override
  public GeoJsonWriterProperties create() {
    return new GeoJsonWriterProperties();
  }

  @Override
  public int getSortPriority() {
    return 2;
  }

  @Override
  public void onPropertiesEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    finalizeCurrent(context);

    next.accept(context);
  }

  @Override
  public void onArrayStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.schema().filter(FeatureSchema::isArray).isPresent()) {
      startIfNecessary(context);
      context.encoding().getJson().writeArrayFieldStart(context.schema().get().getName());
    }

    next.accept(context);
  }

  @Override
  public void onObjectStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.schema().filter(FeatureSchema::isObject).isPresent()) {
      startIfNecessary(context);
      openObject(context, context.schema().get());
    }

    next.accept(context);
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    next.accept(context);

    if (context.schema().filter(FeatureSchema::isObject).isPresent()) {
      FeatureSchema schema = context.schema().get();
      closeObject(context, schema);
    }
  }

  @Override
  public void onArrayEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    next.accept(context);

    if (context.schema().filter(FeatureSchema::isArray).isPresent()) {
      context.encoding().getJson().writeEndArray();
    }
  }

  @Override
  public void onGeometry(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (!context
            .schema()
            .map(
                p ->
                    context
                        .encoding()
                        .getFeatureState()
                        .map(s -> s.primaryGeometryProperty.stream().anyMatch(p::equals))
                        .orElse(false))
            .orElse(false)
        && !context
            .schema()
            .map(
                p ->
                    context
                        .encoding()
                        .getFeatureState()
                        .map(s -> s.secondaryGeometryProperty.stream().anyMatch(p::equals))
                        .orElse(false))
            .orElse(false)
        && context.geometry() != null) {
      startIfNecessary(context);

      context.encoding().getJson().writeFieldName(context.schema().get().getName());
      context
          .geometry()
          .accept(
              new GeometryEncoderJson(
                  context.encoding().getJson(), false, context.encoding().getGeometryPrecision()));
    }

    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (!shouldSkipProperty(context)) {
      FeatureSchema schema = context.schema().get();
      String value = context.value();

      startIfNecessary(context);

      if (schema.isArray() && !context.encoding().getGeoJsonConfig().isFlattened()) {
        writeValue(context.encoding().getJson(), value, getValueType(schema, context.valueType()));
      } else {
        context.encoding().getJson().writeFieldName(schema.getName());
        Type valueType =
            schema.getCoalesce().isEmpty()
                    || (schema.getType() != Type.VALUE && schema.getType() != Type.FEATURE_REF)
                ? schema.getType()
                : getValueType(schema, context.valueType());
        writeValue(context.encoding().getJson(), value, valueType);
      }
    }

    next.accept(context);
  }

  private void startIfNecessary(EncodingAwareContextGeoJson context) throws IOException {
    FeatureState featureState = context.encoding().getFeatureState().get();
    if (!featureState.hasProperties) {
      featureState.hasProperties = true;

      context.encoding().getJson().writeObjectFieldStart(getPropertiesFieldName());
    }
  }

  private Type getValueType(FeatureSchema schema, Type fromValue) {
    return schema
        .getValueType()
        .filter(t -> t != Type.VALUE && t != Type.VALUE_ARRAY)
        .orElse(Objects.requireNonNullElse(fromValue, Type.STRING));
  }

  protected String getPropertiesFieldName() {
    return "properties";
  }

  protected boolean shouldSkipProperty(EncodingAwareContextGeoJson context) {
    return !hasMapping(context)
        || (context.schema().get().isId() || context.schema().get().isEmbeddedId());
  }

  protected boolean hasMapping(EncodingAwareContextGeoJson context) {
    return context.schema().filter(FeatureSchema::isValue).isPresent();
  }

  // TODO: centralize value type mappings (either as transformer or as part of context)
  private void writeValue(JsonGenerator json, String value, Type type) throws IOException {
    if (Objects.isNull(value)) {
      json.writeNull();
      return;
    }

    switch (type) {
      case BOOLEAN:
        // TODO: normalize in decoder
        json.writeBoolean(
            value.equalsIgnoreCase("t") || value.equalsIgnoreCase("true") || value.equals("1"));
        break;
      case INTEGER:
        try {
          json.writeNumber(Long.parseLong(value));
          break;
        } catch (NumberFormatException e) {
          // ignore
        }
      case FLOAT:
        try {
          json.writeNumber(Double.parseDouble(value));
          break;
        } catch (NumberFormatException e) {
          // ignore
        }
      default:
        json.writeString(value);
    }
  }

  private void openObject(EncodingAwareContextGeoJson context, FeatureSchema schema)
      throws IOException {
    if (schema.isArray()) {
      context.encoding().getJson().writeStartObject();
    } else {
      context.encoding().getJson().writeObjectFieldStart(schema.getName());
    }
  }

  private void closeObject(EncodingAwareContextGeoJson context, FeatureSchema schema)
      throws IOException {
    if (schema.isEmbeddedFeature()) {
      finalizeCurrent(context);
    }

    context.encoding().getJson().writeEndObject();
  }

  private void finalizeCurrent(EncodingAwareContextGeoJson context) throws IOException {
    if (context.encoding().getFeatureState().get().hasProperties) {

      // end of "properties"
      context.encoding().getJson().writeEndObject();
    } else {

      // no properties, write null member
      context.encoding().getJson().writeNullField(getPropertiesFieldName());
    }
  }
}
