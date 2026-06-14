/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import jakarta.ws.rs.core.MediaType;
import java.util.Iterator;

/**
 * Insert action. {@link #items()} is a single-use, lazily-pulled iterator over the insert payload;
 * each element is an {@link InsertItem} carrying a fresh {@code InputStream} positioned at the
 * start of one feature in the declared {@link #getMediaType()}, plus identifying context ({@code
 * featureId} when known, 1-based {@code indexInInsert}). The executor must fully drain the iterator
 * before requesting the next action from {@link Transaction#actions()}.
 *
 * <p>Memory bound: at most one feature is held in memory at a time.
 */
public interface TxInsert extends TxAction {

  @Override
  default TxActionType getType() {
    return TxActionType.INSERT;
  }

  MediaType getMediaType();

  /**
   * @return single-use iterator over the feature payloads with identifying context. Must be drained
   *     before any further calls on the parent {@link Transaction#actions()} iterator.
   */
  Iterator<InsertItem> items();
}
