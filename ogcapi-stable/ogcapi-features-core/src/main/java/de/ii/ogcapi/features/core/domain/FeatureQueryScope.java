/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

/**
 * Identifies what produced a features response, which governs link generation and paging: an
 * addressable collection resource, a transient ad-hoc query, or an addressable stored query (paged
 * or single-shot).
 */
public enum FeatureQueryScope {
  /** The items of a single collection (GET .../items): addressable, paged. */
  COLLECTION,
  /**
   * An ad-hoc query expression submitted via POST (.../search): there is no resource on the server
   * to link to and the response is single-shot.
   */
  AD_HOC_QUERY,
  /**
   * A stored query executed via GET (.../search/{queryId}) with paging support: addressable, paged.
   */
  STORED_QUERY,
  /**
   * A stored query executed via GET (.../search/{queryId}) without paging: addressable,
   * single-shot.
   */
  STORED_QUERY_SINGLE_SHOT;

  /** Query expressions (ad-hoc and stored queries) are submitted as a query expression. */
  public boolean isQueryExpression() {
    return this != COLLECTION;
  }

  /** Whether there is an addressable resource that a canonical/self link may point to. */
  public boolean hasCanonicalResource() {
    return this != AD_HOC_QUERY;
  }

  /** Whether the response is paged (and may advertise pagination controls and prev/next links). */
  public boolean supportsPaging() {
    return this == COLLECTION || this == STORED_QUERY;
  }
}
