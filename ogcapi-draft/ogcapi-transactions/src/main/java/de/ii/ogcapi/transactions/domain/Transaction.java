/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import java.util.Iterator;

/**
 * Streaming view of an OGC API Features Part 11 transaction request. The action iterator is lazy:
 * only one action — and within an insert action, only one item — is materialised in memory at a
 * time. Implementations own the underlying body input stream and must release it via {@link
 * #close()}.
 */
public interface Transaction extends AutoCloseable {

  TxSemantic getSemantic();

  /**
   * @return {@code true} if this transaction was parsed from a {@code wfs:Transaction} request,
   *     {@code false} for the OGC API Features Part 11 JSON transaction format. Used by the
   *     executor to pick which feature-format configuration drives property name resolution ({@code
   *     GmlConfiguration.useAlias} vs {@code GeoJsonConfiguration.useAlias}) for {@code wfs:Update}
   *     / JSON update actions.
   */
  default boolean isWfs() {
    return false;
  }

  /**
   * @return a single-use, sequential iterator over the transaction's actions. Actions must be
   *     consumed in order; an insert action's items must be fully drained before advancing to the
   *     next action.
   */
  Iterator<TxAction> actions();

  @Override
  void close();
}
