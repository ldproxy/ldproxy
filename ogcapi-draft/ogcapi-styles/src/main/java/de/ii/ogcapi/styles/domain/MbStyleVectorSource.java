/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true)
@JsonDeserialize(as = ImmutableMbStyleVectorSource.class)
public interface MbStyleVectorSource extends MbStyleSource {

  @Value.Derived
  default String getType() {
    return "vector";
  }

  Optional<String> getUrl();

  Optional<List<String>> getTiles();

  Optional<List<Double>>
      getBounds(); // { return Optional.of(ImmutableList.of(-180.0,-85.051129,180.0,85.051129)); }

  @Value.Default
  default Scheme getScheme() {
    return Scheme.xyz;
  }

  Optional<Number> getMinzoom(); // { return Optional.of(0); }

  Optional<Number> getMaxzoom(); // { return Optional.of(22); }

  Optional<String> getAttribution();
}
