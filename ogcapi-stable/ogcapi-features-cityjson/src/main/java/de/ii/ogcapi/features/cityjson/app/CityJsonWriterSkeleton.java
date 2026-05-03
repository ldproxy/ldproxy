/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.cityjson.domain.CityJsonConfiguration;
import de.ii.ogcapi.features.cityjson.domain.CityJsonWriter;
import de.ii.ogcapi.features.cityjson.domain.EncodingAwareContextCityJson;
import de.ii.ogcapi.features.cityjson.domain.ModifiableStateCityJson;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@AutoBind
public class CityJsonWriterSkeleton implements CityJsonWriter {

  @Inject
  CityJsonWriterSkeleton() {}

  @Override
  public CityJsonWriterSkeleton create() {
    return new CityJsonWriterSkeleton();
  }

  @Override
  public int getSortPriority() {
    return 0;
  }

  @Override
  public void onStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    context.encoding().getJson().writeStartObject();
    context.encoding().getJson().writeStringField(CityJsonWriter.TYPE, CityJsonWriter.CITY_JSON);
    context
        .encoding()
        .getJson()
        .writeStringField(CityJsonWriter.VERSION, context.encoding().getVersion().toString());

    next.accept(context);

    if (context.encoding().getTextSequences()) {
      writeMetadata(context);

      // end of CityJSON object
      context.encoding().getJson().writeEndObject();
      context.encoding().getJson().flush();

    } else {
      context.encoding().getJson().writeFieldName(CityJsonWriter.CITY_OBJECTS);
      context.encoding().getJson().writeStartObject();
    }
  }

  @Override
  public void onEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    if (!context.encoding().getTextSequences()) {
      // end of CityObjects object
      context.encoding().getJson().writeEndObject();
    }

    // next chain for extensions
    next.accept(context);

    if (!context.encoding().getTextSequences()) {
      writeMetadata(context);

      // end of CityJSON object
      context.encoding().getJson().writeEndObject();
    }
  }

  private void writeMetadata(EncodingAwareContextCityJson context) throws IOException {
    context.encoding().getJson().writeFieldName(CityJsonWriter.METADATA);
    context.encoding().getJson().writeStartObject();

    if (context.encoding().getCollection().isPresent()) {
      context
          .encoding()
          .getJson()
          .writeStringField(
              CityJsonWriter.TITLE, context.encoding().getCollection().get().getLabel());
    }

    context
        .encoding()
        .getJson()
        .writeStringField(
            CityJsonWriter.REFERENCE_SYSTEM,
            context.encoding().getVersion().equals(CityJsonConfiguration.Version.V10)
                ? context.encoding().getCrs().toUrnString()
                : context.encoding().getCrs().toUriString());

    if (!context.encoding().getTextSequences()
        && context.getState().getCurrentVertices().isPresent()) {
      Optional<double[]> bbox =
          context
              .getState()
              .getCurrentVertices()
              .get()
              .getBoundingBox()
              .map(
                  ints -> {
                    double[] doubles = new double[6];
                    ModifiableStateCityJson state = context.getState();
                    for (int i = 0; i < ints.length; i++) {
                      doubles[i] =
                          ints[i] * state.getCurrentScale().get(i % 3)
                              + state.getCurrentTranslate().get(i % 3);
                    }
                    return doubles;
                  });
      if (bbox.isPresent()) {
        context.encoding().getJson().writeFieldName(CityJsonWriter.GEOGRAPHICAL_EXTENT);
        context.encoding().getJson().writeArray(bbox.get(), 0, 6);
      }
    }
    context.encoding().getJson().writeEndObject();
  }
}
