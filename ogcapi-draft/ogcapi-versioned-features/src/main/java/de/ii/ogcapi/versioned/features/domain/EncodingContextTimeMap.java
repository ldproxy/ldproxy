/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Resource-level state handed to every {@link FeatureEncoderTimeMap} by the queries handler. The
 * encoder accumulates per-version {@link FeatureVersionTimeMap} instances from the stream and
 * combines them with this context to build the final {@link TimeMap} POJO at end-of-stream.
 */
@Value.Immutable
public interface EncodingContextTimeMap {

  String getCollectionId();

  String getFeatureId();

  /** Canonical resource URI (no query) used to construct memento hrefs. */
  String getFeatureHref();

  /**
   * The collection's HTML feature title template; set only when the output format renders the
   * feature title. The encoder resolves it against each version's properties.
   */
  Optional<String> getFeatureTitleTemplate();

  /** {@code self} + {@code alternate} link entries produced by {@code DefaultLinksGenerator}. */
  List<Link> getResourceLinks();

  OgcApi getApi();

  ApiRequestContext getRequestContext();

  I18n getI18n();

  @Value.Default
  default boolean getPrettify() {
    return false;
  }
}
