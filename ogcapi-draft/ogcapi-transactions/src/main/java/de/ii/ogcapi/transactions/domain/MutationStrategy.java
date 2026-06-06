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
import java.time.Instant;
import java.util.Optional;

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

  /**
   * Resolve the mutation timestamp the strategy will associate with this action.
   *
   * <p>The executor captures a per-scope timestamp ({@code scopeTimestamp}) — once at executor
   * entry for an atomic transaction, per action for a batch transaction — and offers it to the
   * strategy. Plain strategies ignore the inputs and return {@code scopeTimestamp} unchanged.
   * Versioned strategies dispatch on their {@code mutationTime} configuration: {@code server}
   * returns {@code scopeTimestamp}; {@code client} resolves from the action payload's
   * primary-interval-start property when present, falling back to {@code ogcMutationDatetimeHeader}
   * (the {@code OGC-Mutation-Datetime} request header) and ultimately erroring with 400 when
   * neither is supplied.
   *
   * @param apiData the API data, used to look up per-collection configuration
   * @param action the action whose timestamp is being resolved
   * @param scopeTimestamp the per-scope server clock reading captured by the executor
   * @param ogcMutationDatetimeHeader the parsed {@code OGC-Mutation-Datetime} request header, if
   *     supplied by the client
   */
  default Instant resolveMutationTimestamp(
      OgcApiDataV2 apiData,
      TxAction action,
      Instant scopeTimestamp,
      Optional<Instant> ogcMutationDatetimeHeader) {
    return scopeTimestamp;
  }
}
