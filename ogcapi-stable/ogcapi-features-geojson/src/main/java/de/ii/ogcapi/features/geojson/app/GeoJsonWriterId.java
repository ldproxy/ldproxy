/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import de.ii.xtraplatform.strings.domain.StringTemplateFilters;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author zahnen
 */
@Singleton
@AutoBind
public class GeoJsonWriterId implements GeoJsonWriter {

  @Inject
  public GeoJsonWriterId() {}

  @Override
  public GeoJsonWriterId create() {
    return new GeoJsonWriterId();
  }

  @Override
  public int getSortPriority() {
    return 30;
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (context.schema().isPresent() && Objects.nonNull(context.value())) {
      FeatureSchema currentSchema = context.schema().get();

      if (currentSchema.isId() || currentSchema.isEmbeddedId()) {
        String id = context.value();

        // always a string for a multi-collection query
        boolean isInteger =
            currentSchema.getType() == Type.INTEGER
                && context.encoding().getFeatureSchemas().size() == 1;

        context.encoding().pauseBuffering();
        if (isInteger) {
          context.encoding().getJson().writeNumberField("id", Long.parseLong(id));
        } else {
          context.encoding().getJson().writeStringField("id", id);
        }
        context.encoding().continueBuffering();

        if (currentSchema.isId()) {
          addLinks(context, context.value());
        }
      }
    }

    next.accept(context);
  }

  private void addLinks(EncodingAwareContextGeoJson context, String featureId) throws IOException {
    if (context.encoding().isFeatureCollection()
        && Objects.nonNull(featureId)
        && !featureId.isEmpty()) {
      context
          .encoding()
          .getState()
          .addCurrentFeatureLinks(
              new ImmutableLink.Builder()
                  .rel("self")
                  .href(
                      context.encoding().getServiceUrl()
                          + "/collections/"
                          + context.encoding().getCollectionId()
                          + "/items/"
                          + featureId)
                  .build());
      Optional<String> template =
          context
              .encoding()
              .getApiData()
              .getCollections()
              .get(context.encoding().getCollectionId())
              .getPersistentUriTemplate();
      if (template.isPresent()) {
        context
            .encoding()
            .getState()
            .addCurrentFeatureLinks(
                new ImmutableLink.Builder()
                    .rel("canonical")
                    .href(StringTemplateFilters.applyTemplate(template.get(), featureId))
                    .build());
      }
    }
  }
}
