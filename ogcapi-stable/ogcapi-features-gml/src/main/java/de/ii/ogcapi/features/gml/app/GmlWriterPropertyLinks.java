/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGml;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.xtraplatform.features.domain.PropertyLink;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Renders per-feature property links (populated by {@code FeatureTokenTransformerPropertyLinks}) as
 * XML comments inside the feature element, immediately after the start tag. GML elements cannot
 * carry RFC 8288 web links, so the information that other formats expose in {@code links} entries
 * is preserved here as a {@code <!-- <rel>: <value> -->} comment per link — the captured property
 * value, not the resolved link URI. The corresponding HTTP {@code Link} response header is set by
 * the queries handler regardless of format.
 *
 * <p>Sort priority 60 — runs after the other writers in {@code onFeatureStart}, so the comment
 * placeholder lands after {@code gml:identifier} (if configured) but before any property elements.
 * The placeholder is populated in {@code onFeatureEnd} once {@code
 * FeatureTokenTransformerPropertyLinks} has surfaced the captures on the context.
 */
@Singleton
@AutoBind
public class GmlWriterPropertyLinks implements GmlWriter {

  private String placeholder;

  @Inject
  public GmlWriterPropertyLinks() {}

  @Override
  public GmlWriterPropertyLinks create() {
    return new GmlWriterPropertyLinks();
  }

  @Override
  public int getSortPriority() {
    return 60;
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next) throws IOException {
    next.accept(context);
    this.placeholder = context.encoding().reservePropertyLinksPlaceholder();
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    next.accept(context);

    List<PropertyLink> propertyLinks = context.propertyLinks();
    if (Objects.isNull(placeholder) || propertyLinks.isEmpty()) {
      return;
    }

    // The placeholder was emitted as `<!-- KEY -->`. Replacement substitutes KEY only.
    // For multiple links, the content embeds `--><!--` between entries so the result is
    // one comment per link: `<!-- rel1: v1 --><!-- rel2: v2 -->`.
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (PropertyLink link : propertyLinks) {
      if (!first) {
        sb.append(" --><!-- ");
      }
      first = false;
      sb.append(link.getRel()).append(": ").append(escapeCommentText(link.getValue()));
    }
    context.encoding().setPropertyLinksContent(placeholder, sb.toString());
  }

  private static String escapeCommentText(String value) {
    return value.replace("--", "-‐");
  }
}
