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
import de.ii.ogcapi.features.cityjson.domain.FeatureTransformationContextCityJson;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

@Singleton
@AutoBind
public class CityJsonWriterFeature implements CityJsonWriter {

  @Inject
  CityJsonWriterFeature() {}

  @Override
  public CityJsonWriterFeature create() {
    return new CityJsonWriterFeature();
  }

  @Override
  public int getSortPriority() {
    return 10;
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    context
        .getState()
        .setInSection(FeatureTransformationContextCityJson.StateCityJson.Section.IN_BUILDING);
    context.getState().getCurrentChildren().clear();

    if (context.encoding().getTextSequences()) {
      context.encoding().getJson().writeStartObject();
      context.encoding().getJson().writeStringField(TYPE, CityJsonWriter.CITY_JSON_FEATURE);
      context.encoding().getJson().writeFieldName(CITY_OBJECTS);
      context.encoding().getJson().writeStartObject();
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    context.encoding().getJson().writeFieldName(getId(context));
    context.encoding().getJson().writeStartObject();
    // the only top-level features are buildings
    context.encoding().getJson().writeStringField(TYPE, CityJsonWriter.BUILDING);

    if (!context.getState().getCurrentChildren().isEmpty()) {
      context.encoding().getJson().writeFieldName(CityJsonWriter.CHILDREN);
      context.encoding().getJson().writeStartArray();
      for (String child : context.getState().getCurrentChildren()) {
        context.encoding().getJson().writeString(child);
      }
      context.encoding().getJson().writeEndArray();
    }

    next.accept(context);

    // end of Building object
    context.encoding().getJson().writeEndObject();

    context
        .getState()
        .setInSection(FeatureTransformationContextCityJson.StateCityJson.Section.OUTSIDE);
  }

  @Override
  public void onFeatureEndEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.encoding().getTextSequences()) {
      // close CityObjects object
      context.encoding().getJson().writeEndObject();
    }

    next.accept(context);

    if (context.encoding().getTextSequences()) {
      // close CityJSONFeature object
      context.encoding().getJson().writeStringField(ID, getId(context));
      context.encoding().getJson().writeEndObject();
      context.encoding().getJson().flush();
    }
  }

  @Override
  public void onArrayStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.schema().isPresent()
        && context.schema().get().isArray()
        && CityJsonWriter.CONSISTS_OF_BUILDING_PART.equals(context.schema().get().getName())) {
      context.getState().setInBuildingPart(true);
    }

    next.accept(context);
  }

  @Override
  public void onArrayEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);

    if (context.schema().isPresent()
        && context.schema().get().isArray()
        && CityJsonWriter.CONSISTS_OF_BUILDING_PART.equals(context.schema().get().getName())) {
      context.getState().setInBuildingPart(false);
    }
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.schema().isPresent()
        && context.schema().get().isObject()
        && context.getState().inBuildingPart()
        && CityJsonWriter.CONSISTS_OF_BUILDING_PART.equals(context.schema().get().getName())) {

      String buildingPartId = getId(context);
      context.encoding().getJson().writeFieldName(buildingPartId);
      context.encoding().getJson().writeStartObject();
      context.encoding().getJson().writeStringField(TYPE, CityJsonWriter.BUILDING_PART);

      context.getState().addCurrentChildren(buildingPartId);
      context.encoding().getJson().writeFieldName(CityJsonWriter.PARENTS);
      context.encoding().getJson().writeStartArray();
      context.encoding().getJson().writeString(getId(context, true));
      context.encoding().getJson().writeEndArray();

      next.accept(context);

      // end of Building object
      context.encoding().getJson().writeEndObject();
    } else {
      next.accept(context);
    }
  }

  private String getId(EncodingAwareContextCityJson context) {
    return getId(context, false);
  }

  private String getId(EncodingAwareContextCityJson context, boolean building) {
    return (context.getState().inBuildingPart() && !building
            ? context.getState().getCurrentIdBuildingPart()
            : context.getState().getCurrentId())
        // if there is no id, we generate a UUID
        .orElse(UUID.randomUUID().toString());
  }
}
