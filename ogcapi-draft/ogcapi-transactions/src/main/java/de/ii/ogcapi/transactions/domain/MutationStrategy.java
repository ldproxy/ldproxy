/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;

/**
 * Per-collection mutation behaviour applied by the transaction executor.
 *
 * <p>Implementations are discovered via the {@code ExtensionRegistry}; for a given collection the
 * executor picks the highest-{@link #priority() priority} strategy whose {@link
 * ApiExtension#isEnabledForApi(OgcApiDataV2, String)} returns {@code true} — i.e. the strategy's
 * declared building-block configuration is enabled on that collection. The default plain strategy
 * binds to {@code TransactionsConfiguration} and therefore applies whenever the Transactions
 * building block itself is enabled, so the executor always finds a strategy when it runs.
 *
 * <p>The interface itself carries no per-action methods yet. Phase 1.1 (mutation-timestamp
 * resolution) introduces the first hook; Phase 1.2-1.6 add the {@code Insert} / {@code Replace} /
 * {@code Update} / {@code Delete} hooks that diverge between plain and versioned collections.
 */
public interface MutationStrategy extends ApiExtension {

  /**
   * Strategies with a higher priority win when more than one is enabled for the same collection.
   * The default plain strategy uses {@code 0}; specialised strategies (e.g. versioned collections)
   * override with a larger value.
   */
  default int priority() {
    return 0;
  }
}
