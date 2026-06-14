/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.xtraplatform.crs.domain.EpsgCrs;

/** Executes a parsed {@link Transaction} against the feature providers backing the API. */
public interface TransactionExecutor {

  /**
   * @param transaction parsed request. The executor consumes the actions iterator and closes the
   *     transaction.
   * @param api target API
   * @param requestContext request context (used for collection/provider lookups)
   * @param requestCrs CRS to assume for feature payloads when not otherwise specified
   * @param validate when {@code true}, each insert / replace payload is validated against its
   *     target feature type's schema (via {@code FeatureFormatExtension.validate}) before any
   *     provider write. Set by the endpoint when the client sends {@code Prefer: handling=strict}.
   *     Per-feature failures inside an atomic transaction abort the transaction; failures inside a
   *     batch transaction skip the offending item and surface it through {@link
   *     ActionResult#getFailedFeatureIds()} / {@link ActionResult#getFailedFeatureIndexes()}.
   * @return summary of what happened, in request order
   */
  ExecutionResult execute(
      Transaction transaction,
      OgcApi api,
      ApiRequestContext requestContext,
      EpsgCrs requestCrs,
      boolean validate);
}
