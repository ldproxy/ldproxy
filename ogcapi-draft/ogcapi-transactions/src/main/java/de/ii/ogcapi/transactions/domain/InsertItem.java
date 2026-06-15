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
 * {@code id} member for GeoJSON) when available. {@code payloadBytes} carries the raw feature
 * bytes; the executor uses them as a locator-of-last-resort when the item has no {@code featureId}
 * and a failure has to be reported back to the client.
 */
public final class InsertItem {

  private final Optional<String> featureId;
  private final byte[] payloadBytes;
  private final InputStream payload;

  public InsertItem(Optional<String> featureId, byte[] payloadBytes, InputStream payload) {
    this.featureId = featureId;
    this.payloadBytes = payloadBytes;
    this.payload = payload;
  }

  public Optional<String> featureId() {
    return featureId;
  }

  /**
   * Raw bytes of the feature payload as sent by the client. Used by the executor to identify a
   * failing item that has no {@code featureId}; never modified.
   */
  public byte[] payloadBytes() {
    return payloadBytes;
  }

  public InputStream payload() {
    return payload;
  }
}
