/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import java.io.InputStream;
import java.util.Optional;

/**
 * One feature payload inside a {@link TxInsert}, with identifying context.
 *
 * <p>{@code featureId} is the source-side identifier (e.g. {@code gml:id} for GML payloads, the
 * {@code id} member for GeoJSON) when available. {@code indexInInsert} is the 1-based position of
 * this item within its originating {@code wfs:Insert} / transaction {@code items} array — useful
 * when no source id is present.
 */
public final class InsertItem {

  private final Optional<String> featureId;
  private final int indexInInsert;
  private final InputStream payload;

  public InsertItem(Optional<String> featureId, int indexInInsert, InputStream payload) {
    this.featureId = featureId;
    this.indexInInsert = indexInInsert;
    this.payload = payload;
  }

  public Optional<String> featureId() {
    return featureId;
  }

  public int indexInInsert() {
    return indexInInsert;
  }

  public InputStream payload() {
    return payload;
  }
}
