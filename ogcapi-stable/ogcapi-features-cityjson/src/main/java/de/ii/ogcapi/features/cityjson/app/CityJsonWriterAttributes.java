/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.cityjson.domain.CityJsonWriter;
import de.ii.ogcapi.features.cityjson.domain.EncodingAwareContextCityJson;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Singleton
@AutoBind
public class CityJsonWriterAttributes implements CityJsonWriter {

  @Inject
  CityJsonWriterAttributes() {}

  @Override
  public CityJsonWriterAttributes create() {
    return new CityJsonWriterAttributes();
  }

  // must be after the writers with sections (address, etc)
  @Override
  public int getSortPriority() {
    return 100;
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    context.encoding().startAttributes();

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    context.encoding().stopAndFlushAttributes();

    next.accept(context);
  }

  @Override
  public void onObjectStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.schema().isPresent() && context.schema().get().isObject()) {
      FeatureSchema schema = context.schema().get();

      if (context.getState().inBuildingPart()
          && CONSISTS_OF_BUILDING_PART.equals(schema.getName())) {
        context.encoding().startAttributes();

      } else if (context.getState().atTopLevel()
          && context.getState().getAttributesBuffer().isPresent()) {
        if (schema.isArray()) {
          context.getState().getAttributesBuffer().get().writeStartObject();
        } else {
          context.getState().getAttributesBuffer().get().writeObjectFieldStart(schema.getName());
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.schema().isPresent() && context.schema().get().isObject()) {
      FeatureSchema schema = context.schema().get();

      if (context.getState().inBuildingPart()
          && CONSISTS_OF_BUILDING_PART.equals(schema.getName())) {
        context.encoding().stopAndFlushAttributes();

      } else if (context.getState().atTopLevel()
          && context.getState().getAttributesBuffer().isPresent()) {
        context.getState().getAttributesBuffer().get().writeEndObject();
      }
    }

    next.accept(context);
  }

  @Override
  public void onArrayStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.schema().isPresent() && context.schema().get().isArray()) {
      FeatureSchema schema = context.schema().get();

      if (!CONSISTS_OF_BUILDING_PART.equals(schema.getName())) {
        if (context.getState().atTopLevel()
            && context.getState().getAttributesBuffer().isPresent()) {
          String name = schema.getName();
          context.getState().getAttributesBuffer().get().writeArrayFieldStart(name);
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onArrayEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.schema().isPresent() && context.schema().get().isArray()) {
      FeatureSchema schema = context.schema().get();

      if (!CONSISTS_OF_BUILDING_PART.equals(schema.getName())
          && context.getState().atTopLevel()
          && context.getState().getAttributesBuffer().isPresent()) {
        context.getState().getAttributesBuffer().get().writeEndArray();
      }
    }

    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    if (context.getState().atTopLevel()
        && hasMappingAndValue(context)
        && context.schema().isPresent()) {
      FeatureSchema schema = context.schema().get();
      String value = context.value();

      if (schema.isArray()) {
        context
            .encoding()
            .writeAttributeValue(value, schema.getValueType().orElse(SchemaBase.Type.STRING));
      } else if (!schema.isId()
          && !(context.getState().inBuildingPart() && CityJsonWriter.ID.equals(schema.getName()))
          && context.getState().getAttributesBuffer().isPresent()) {
        String name = schema.getName();
        if (!"gml_id".equals(name)) {
          context.getState().getAttributesBuffer().get().writeFieldName(name);
          context.encoding().writeAttributeValue(value, schema.getType());
        }
      }
    }

    next.accept(context);
  }

  private boolean hasMappingAndValue(EncodingAwareContextCityJson context) {
    return context.schema().map(FeatureSchema::isValue).orElse(false)
        && Objects.nonNull(context.value());
  }
}
