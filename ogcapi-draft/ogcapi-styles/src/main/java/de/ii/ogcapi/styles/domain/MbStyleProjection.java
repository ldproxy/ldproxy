/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true)
@JsonDeserialize(as = ImmutableMbStyleProjection.class)
public abstract class MbStyleProjection {

  /**
   * The projection: a name such as `mercator`, `globe` or the name of a coordinate reference system
   * registered in the client, e.g. `EPSG:25832`, or an expression that switches between projections
   * by zoom level.
   */
  public abstract Object getType();
}
