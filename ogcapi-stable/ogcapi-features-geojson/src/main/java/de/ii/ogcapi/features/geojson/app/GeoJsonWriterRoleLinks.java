/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Renders per-feature role-as-link captures (populated by {@code FeatureTokenTransformerLinkRoles})
 * as feature-level link entries in the GeoJSON body. Each entry in {@link
 * de.ii.xtraplatform.features.domain.FeatureEventHandler.ModifiableContext#roleLinks()} is added to
 * the encoder's per-feature link collection with an href that points to the same feature at the
 * captured timestamp ({@code ?datetime=<value>}). {@code GeoJsonWriterLinks} (sort priority 50)
 * then emits them in the response body's {@code links} array.
 *
 * <p>Sort priority 45 — runs after {@code GeoJsonWriterId} (30, contributes the self link) and
 * before {@code GeoJsonWriterLinks} (50, writes the array).
 */
@Singleton
@AutoBind
public class GeoJsonWriterRoleLinks implements GeoJsonWriter {

  private final I18n i18n;
  private String featureId;

  @Inject
  public GeoJsonWriterRoleLinks(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public GeoJsonWriterRoleLinks create() {
    return new GeoJsonWriterRoleLinks(i18n);
  }

  private static String relToI18nKey(String rel) {
    String[] parts = rel.split("-");
    StringBuilder sb = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
    }
    sb.append("Link");
    return sb.toString();
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
    Map<String, String> roleLinks = context.roleLinks();
    // When the composite-id profile rewrote the id token, the canonical id is stashed on the
    // context. Build version/predecessor/successor URLs against the CANONICAL id so the path
    // resolves to the stable resource — the JSON `id` carries the composite, but the URL paths
    // always identify by canonical id.
    String canonicalId = context.canonicalFeatureId();
    String urlId = Objects.nonNull(canonicalId) ? canonicalId : featureId;
    if (Objects.nonNull(urlId)) {
      String base =
          context.encoding().getServiceUrl()
              + "/collections/"
              + context.encoding().getCollectionId()
              + "/items/"
              + urlId;
      for (Map.Entry<String, String> entry : roleLinks.entrySet()) {
        context
            .encoding()
            .getState()
            .addCurrentFeatureLinks(
                new ImmutableLink.Builder()
                    .rel(entry.getKey())
                    .href(base + "?datetime=" + entry.getValue())
                    .title(i18n.get(relToI18nKey(entry.getKey()), context.encoding().getLanguage()))
                    .build());
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
                    .href(base)
                    .title(i18n.get("originalLink", context.encoding().getLanguage()))
                    .build());
      }
    }
    next.accept(context);
  }
}
