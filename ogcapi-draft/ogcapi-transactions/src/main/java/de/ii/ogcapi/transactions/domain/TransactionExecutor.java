/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import java.time.Instant;
import java.util.Optional;

/** Executes a parsed {@link Transaction} against the feature providers backing the API. */
public interface TransactionExecutor extends Volatile2 {

  /**
   * @param transaction parsed request. The executor consumes the actions iterator and closes the
   *     transaction.
   * @param api target API
   * @param requestContext request context (used for collection/provider lookups)
   * @param requestCrs CRS to assume for feature payloads when not otherwise specified
   * @param handling the request's {@code Prefer: handling} preference. {@code STRICT} turns on
   *     payload validation against each target feature type's schema (via {@code
   *     FeatureFormatExtension.validate}) before any provider write — per-feature failures inside
   *     an atomic transaction abort the transaction, failures inside a batch transaction skip the
   *     offending item and surface it through {@link ActionResult#getFailedFeatureIds()} / {@link
   *     ActionResult#getFailedFeaturePayloads()}. It also selects which transaction-lifecycle hook
   *     statements run (see {@code TransactionsConfiguration}).
   * @param ogcMutationDatetime parsed {@code OGC-Mutation-Datetime} request header, if supplied —
   *     used by versioned strategies in {@code client} mutation-time mode when an action carries no
   *     body-side primary-interval-start value
   * @return summary of what happened, in request order
   */
  ExecutionResult execute(
      Transaction transaction,
      OgcApi api,
      ApiRequestContext requestContext,
      EpsgCrs requestCrs,
      HeaderPrefer.Handling handling,
      Optional<Instant> ogcMutationDatetime);
}
