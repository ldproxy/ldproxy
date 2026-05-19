/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/** Per-action result of an executed transaction. */
@Value.Immutable
public interface ActionResult {

  /** Action type (INSERT/REPLACE/UPDATE/DELETE). */
  TxActionType getType();

  /** Target collection (always present, even on FAILED actions). */
  String getCollectionId();

  /** {@code actionId} from the request, used to correlate the response with the request. */
  Optional<String> getActionId();

  ActionStatus getStatus();

  /** Feature identifiers affected by this action. Empty for SKIPPED / FAILED outcomes. */
  List<String> getFeatureIds();

  /**
   * Human-readable error message for FAILED outcomes. Empty for SUCCESS / SKIPPED.
   *
   * <p>Implementations should put the spec-defined exception code into the message or attach
   * additional structured metadata via a follow-up extension.
   */
  Optional<String> getError();
}
