/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

public enum SchemaType {
  QUERYABLES,
  SORTABLES,
  RETURNABLES_AND_RECEIVABLES,
  STORED_QUERY_PARAMETER;

  @Override
  public String toString() {
    return this.name().toLowerCase();
  }
}
