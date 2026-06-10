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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Renders per-feature role-as-link captures (populated by {@code FeatureTokenTransformerLinkRoles})
 * as XML comments inside the feature element, immediately after the start tag. GML elements cannot
 * carry RFC 8288 web links, so the predecessor/successor information that other formats expose in
 * {@code links} entries is preserved here as a {@code <!-- <rel>: <value> -->} comment per role.
 * The corresponding HTTP {@code Link} response header is set by the queries handler regardless of
 * format.
 *
 * <p>Sort priority 60 — runs after the other writers in {@code onFeatureStart}, so the comment
 * placeholder lands after {@code gml:identifier} (if configured) but before any property elements.
 * The placeholder is populated in {@code onFeatureEnd} once {@code
 * FeatureTokenTransformerLinkRoles} has surfaced the captures on the context.
 */
@Singleton
@AutoBind
public class GmlWriterRoleLinks implements GmlWriter {

  private String placeholder;

  @Inject
  public GmlWriterRoleLinks() {}

  @Override
  public GmlWriterRoleLinks create() {
    return new GmlWriterRoleLinks();
  }

  @Override
  public int getSortPriority() {
    return 60;
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next) throws IOException {
    next.accept(context);
    this.placeholder = context.encoding().reserveRoleLinksPlaceholder();
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    next.accept(context);

    Map<String, String> roleLinks = context.roleLinks();
    if (Objects.isNull(placeholder) || roleLinks.isEmpty()) {
      return;
    }

    // The placeholder was emitted as `<!-- KEY -->`. Replacement substitutes KEY only.
    // For multiple roles, the content embeds `--><!--` between entries so the result is
    // one comment per role: `<!-- rel1: v1 --><!-- rel2: v2 -->`.
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : roleLinks.entrySet()) {
      if (!first) {
        sb.append(" --><!-- ");
      }
      first = false;
      sb.append(entry.getKey()).append(": ").append(escapeCommentText(entry.getValue()));
    }
    context.encoding().setRoleLinksContent(placeholder, sb.toString());
  }

  private static String escapeCommentText(String value) {
    return value.replace("--", "-‐");
  }
}
