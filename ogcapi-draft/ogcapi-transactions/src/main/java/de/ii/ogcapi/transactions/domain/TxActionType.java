/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

public enum TxActionType {
  INSERT,
  REPLACE,
  UPDATE,
  DELETE;

  public String toJsonValue() {
    return name().toLowerCase();
  }

  public static TxActionType fromJsonValue(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Action type is required");
    }
    for (TxActionType t : values()) {
      if (t.toJsonValue().equalsIgnoreCase(value)) {
        return t;
      }
    }
    throw new IllegalArgumentException(
        "Invalid action type: '"
            + value
            + "' (expected 'insert', 'replace', 'update' or 'delete')");
  }
}
