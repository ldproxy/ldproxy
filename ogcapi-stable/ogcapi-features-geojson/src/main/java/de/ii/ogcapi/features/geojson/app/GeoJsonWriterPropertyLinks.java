/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.PropertyLinkResolver;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.PropertyLink;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Renders per-feature property links (populated by {@code FeatureTokenTransformerPropertyLinks}) as
 * feature-level link entries in the GeoJSON body. Each entry in {@link
 * de.ii.xtraplatform.features.domain.FeatureEventHandler.ModifiableContext#propertyLinks()} is
 * added to the encoder's per-feature link collection with the resolved URI template. {@code
 * GeoJsonWriterLinks} (sort priority 50) then emits them in the response body's {@code links}
 * array.
 *
 * <p>Sort priority 45 — runs after {@code GeoJsonWriterId} (30, contributes the self link) and
 * before {@code GeoJsonWriterLinks} (50, writes the array).
 */
@Singleton
@AutoBind
public class GeoJsonWriterPropertyLinks implements GeoJsonWriter {

  private final I18n i18n;
  private String featureId;
  private String featureType;

  @Inject
  public GeoJsonWriterPropertyLinks(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public GeoJsonWriterPropertyLinks create() {
    return new GeoJsonWriterPropertyLinks(i18n);
  }

  @Override
  public int getSortPriority() {
    return 45;
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    this.featureId = null;
    this.featureType = context.type();
    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (Objects.isNull(featureId) && context.schema().isPresent()) {
      FeatureSchema schema = context.schema().get();
      if (schema.isId() && Objects.nonNull(context.value())) {
        featureId = context.value();
      }
    }
    next.accept(context);
  }

  @Override
  public void onPropertiesEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    // When the composite-id profile rewrote the id token, the canonical id is stashed on the
    // context. Build the feature URI with the CANONICAL id so the path resolves to the stable
    // resource — the JSON `id` carries the composite, but the URL paths always identify by
    // canonical id.
    String canonicalId = context.canonicalFeatureId();
    String baseId = Objects.nonNull(canonicalId) ? canonicalId : featureId;
    // Resolve the feature's own collection from its type, then strip the collection prefix the
    // multi-collection response may have added to the id, so links point at /collections/{cid}/
    // items/{id}.
    String collectionId = context.encoding().getCollectionIdForType(featureType);
    String urlId = context.encoding().getFeatureIdInPath(baseId, collectionId);
    if (Objects.nonNull(urlId)) {
      String apiUri = context.encoding().getServiceUrl();
      String collectionUri = apiUri + "/collections/" + collectionId;
      String featureUri = collectionUri + "/items/" + urlId;
      for (PropertyLink link : context.propertyLinks()) {
        ImmutableLink.Builder builder =
            new ImmutableLink.Builder()
                .rel(link.getRel())
                .href(PropertyLinkResolver.resolve(link, apiUri, collectionUri, featureUri));
        title(link, context.encoding().getLanguage()).ifPresent(builder::title);
        context.encoding().getState().addCurrentFeatureLinks(builder.build());
      }
      // Surface the stable feature URI as an `original` link whenever the id was rewritten
      // (composite-id active for this feature).
      if (Objects.nonNull(canonicalId)) {
        context
            .encoding()
            .getState()
            .addCurrentFeatureLinks(
                new ImmutableLink.Builder()
                    .rel("original")
                    .href(featureUri)
                    .title(i18n.get("originalLink", context.encoding().getLanguage()))
                    .build());
      }
    }
    next.accept(context);
  }

  // The RFC 7089 version-navigation rels have localized titles; for other rels the configured
  // property label is used, if present.
  private Optional<String> title(PropertyLink link, Optional<Locale> language) {
    if ("predecessor-version".equals(link.getRel())) {
      return Optional.of(i18n.get("predecessorVersionLink", language));
    }
    if ("successor-version".equals(link.getRel())) {
      return Optional.of(i18n.get("successorVersionLink", language));
    }
    return link.getTitle();
  }
}
