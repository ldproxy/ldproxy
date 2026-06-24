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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

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
          addLinks(context, context.value(), context.type());
        }
      }
    }

    next.accept(context);
  }

  private void addLinks(EncodingAwareContextGeoJson context, String featureId, String type)
      throws IOException {
    if (context.encoding().isFeatureCollection()
        && Objects.nonNull(featureId)
        && !featureId.isEmpty()) {
      // In a multi-collection response the path must use the feature's own collection and the bare
      // id; the JSON id may be qualified with a collection prefix to stay unique.
      String collectionId = context.encoding().getCollectionIdForType(type);
      String pathId = context.encoding().getFeatureIdInPath(featureId, collectionId);
      context
          .encoding()
          .getState()
          .addCurrentFeatureLinks(
              new ImmutableLink.Builder()
                  .rel("self")
                  .href(
                      context.encoding().getServiceUrl()
                          + "/collections/"
                          + collectionId
                          + "/items/"
                          + pathId)
                  .build());
      Optional<String> template =
          context
              .encoding()
              .getApiData()
              .getCollections()
              .get(collectionId)
              .getPersistentUriTemplate();
      if (template.isPresent()) {
        context
            .encoding()
            .getState()
            .addCurrentFeatureLinks(
                new ImmutableLink.Builder()
                    .rel("canonical")
                    .href(StringTemplateFilters.applyTemplate(template.get(), pathId))
                    .build());
      }
    }
  }
}
