/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

/** Outcome of a single action in an executed transaction. */
public enum ActionStatus {
  /** Action ran and made changes. */
  SUCCESS,

  /**
   * Atomic-only: action was reached but not executed because a sibling action failed and the
   * transaction was rolled back, or never reached because execution stopped earlier.
   */
  SKIPPED,

  /** Action ran or was rejected before any change was made and produced an error. */
  FAILED
}
