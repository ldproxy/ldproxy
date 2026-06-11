/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import java.util.Optional;

/** Common contract for an in-memory transaction action. */
public interface TxAction {

  TxActionType getType();

  String getCollectionId();

  /** Optional action identifier from the request, used to correlate exception reports. */
  Optional<String> getActionId();

  Optional<String> getTitle();

  Optional<String> getDescription();
}
