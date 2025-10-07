/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

public enum SpecificationMaturity {
  STABLE_OGC("Maturity: `STABLE`"),
  DRAFT_OGC(
      "Maturity: `PRELIMINARY` (specified in a draft standard; the %s may change in this API)"),
  STABLE_IETF("Maturity: `STABLE`"),
  DRAFT_IETF(
      "Maturity: `PRELIMINARY` (specified in a draft standard; the %s may change in this API)"),
  STABLE_LDPROXY("Maturity: `STABLE`"),
  DRAFT_LDPROXY("Maturity: `PRELIMINARY` (the %s may change in this API)"),
  DEPRECATED("Maturity: `DEPRECATED` (the %s may be removed in this API)");

  private final String text;

  SpecificationMaturity(String text) {
    this.text = text;
  }

  @Override
  public String toString() {
    return text;
  }
}
