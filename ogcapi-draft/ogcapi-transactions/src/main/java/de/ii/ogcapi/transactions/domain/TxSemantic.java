/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

public enum TxSemantic {
  ATOMIC,
  BATCH;

  public String toJsonValue() {
    return name().toLowerCase();
  }

  public static TxSemantic fromJsonValue(String value) {
    if (value == null) {
      return ATOMIC;
    }
    for (TxSemantic s : values()) {
      if (s.toJsonValue().equalsIgnoreCase(value)) {
        return s;
      }
    }
    throw new IllegalArgumentException(
        "Invalid transaction semantic: '" + value + "' (expected 'atomic' or 'batch')");
  }
}
