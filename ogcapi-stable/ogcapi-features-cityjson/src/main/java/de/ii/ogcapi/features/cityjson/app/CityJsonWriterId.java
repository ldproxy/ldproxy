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
import de.ii.xtraplatform.features.domain.FeatureSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Singleton
@AutoBind
public class CityJsonWriterId implements CityJsonWriter {

  @Inject
  public CityJsonWriterId() {}

  @Override
  public CityJsonWriterId create() {
    return new CityJsonWriterId();
  }

  @Override
  public int getSortPriority() {
    return 20;
  }

  @Override
  public void onValue(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    if (context.getState().inSection()
            == FeatureTransformationContextCityJson.StateCityJson.Section.IN_BUILDING
        && context.schema().isPresent()
        && Objects.nonNull(context.value())) {
      FeatureSchema schema = context.schema().get();

      if (schema.isId()) {
        //noinspection ConstantConditions
        context.getState().setCurrentId(context.value());
      } else if (context.getState().inBuildingPart()
          && CityJsonWriter.ID.equals(schema.getName())) {
        //noinspection ConstantConditions
        context.getState().setCurrentIdBuildingPart(context.value());
      }
    }

    next.accept(context);
  }
}
