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
@JsonDeserialize(as = ImmutableMbStyleRasterDemSource.class)
public interface MbStyleRasterDemSource extends MbStyleSource {
  enum Encoding {
    terrarium,
    mapbox
  }

  @Value.Derived
  default String getType() {
    return "raster-dem";
  }

  Optional<String> getUrl();

  Optional<List<String>> getTiles();

  Optional<List<Double>>
      getBounds(); // { return Optional.of(ImmutableList.of(-180.0,-85.051129,180.0,85.051129)); }

  Optional<Integer> getTileSize(); // { return Optional.of(512); }

  Optional<Number> getMinzoom(); // { return Optional.of(0); }

  Optional<Number> getMaxzoom(); // { return Optional.of(22); }

  Optional<String> getAttribution();

  @Value.Default
  default Encoding getEncoding() {
    return Encoding.mapbox;
  }
}
