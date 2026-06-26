/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

/**
 * Identifies what produced a features response, which governs link generation: an addressable
 * collection resource, a transient ad-hoc query, or an addressable stored query.
 */
public enum FeatureQueryScope {
  /** The items of a single collection (GET .../items): addressable and pageable. */
  COLLECTION,
  /**
   * An ad-hoc query expression submitted via POST (.../search): there is no resource on the server
   * to link to and the response is single-shot.
   */
  AD_HOC_QUERY,
  /** A stored query executed via GET (.../search/{queryId}): addressable, but single-shot. */
  STORED_QUERY;

  /** Query expressions (ad-hoc and stored queries) are single-shot and not paged. */
  public boolean isQueryExpression() {
    return this != COLLECTION;
  }

  /** Whether there is an addressable resource that a canonical/self link may point to. */
  public boolean hasCanonicalResource() {
    return this != AD_HOC_QUERY;
  }
}
