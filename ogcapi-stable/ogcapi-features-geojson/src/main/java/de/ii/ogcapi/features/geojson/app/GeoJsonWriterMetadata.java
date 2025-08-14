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
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.OptionalLong;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author zahnen
 */
@Singleton
@AutoBind
public class GeoJsonWriterMetadata implements GeoJsonWriter {

  @Inject
  public GeoJsonWriterMetadata() {}

  @Override
  public GeoJsonWriterMetadata create() {
    return new GeoJsonWriterMetadata();
  }

  @Override
  public int getSortPriority() {
    return 15;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (context.encoding().isFeatureCollection()) {
      OptionalLong numberReturned = context.metadata().getNumberReturned();
      OptionalLong numberMatched = context.metadata().getNumberMatched();

      /* TODO
      boolean isLastPage = numberReturned.orElse(0) < context.query().getLimit();
      this.writeLinksIfAny(context.encoding().getJson(), context.encoding().getLinks(), isLastPage);
       */

      if (numberReturned.isPresent()) {
        context.encoding().getJson().writeNumberField("numberReturned", numberReturned.getAsLong());
      }
      if (numberMatched.isPresent()) {
        context.encoding().getJson().writeNumberField("numberMatched", numberMatched.getAsLong());
      }
      context
          .encoding()
          .getJson()
          .writeStringField("timeStamp", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
    }

    next.accept(context);
  }

  /* TODO
  @Override
  public void onFeatureStart(EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next) throws IOException {
      if (!context.encoding().isFeatureCollection()) {
          this.writeLinksIfAny(context.encoding().getJson(), context.encoding().getLinks(), false);
      }

      // next chain for extensions
      next.accept(context);
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next) throws IOException {
      // next chain for extensions
      next.accept(context);

      if (!context.encoding().isFeatureCollection()) {
          this.writeLinksIfAny(context.encoding().getJson(), context.encoding().getLinks(), false);
      }
  }

  private void writeLinksIfAny(JsonGenerator json, List<Link> links, boolean isLastPage) throws IOException {
      if (!links.isEmpty()) {
          json.writeFieldName("links");
          json.writeStartArray();

          for (Link link : links) {
              if (!(isLastPage && Objects.equals(link.getRel(), "next"))) {
                  json.writeStartObject();
                  json.writeStringField("href", link.getHref());
                  if (Objects.nonNull(link.getRel()))
                      json.writeStringField("rel", link.getRel());
                  if (Objects.nonNull(link.getType()))
                      json.writeStringField("type", link.getType());
                  if (Objects.nonNull(link.getTitle()))
                      json.writeStringField("title", link.getTitle());
                  json.writeEndObject();
              }
          }

          json.writeEndArray();
      }
  }
   */
}
