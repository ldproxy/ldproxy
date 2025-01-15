/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true)
@JsonDeserialize(as = ImmutableMbStyleGeojsonSource.class)
public interface MbStyleGeojsonSource extends MbStyleSource {
  @Value.Derived
  default String getType() {
    return "geojson";
  }

  Optional<Object> getData();

  @Value.Default
  default Number getMaxzoom() {
    return 18;
  }

  Optional<String> getAttribution();

  Optional<Integer> getBuffer(); // { return Optional.of(128); }

  Optional<Double> getTolerance(); // { return Optional.of(0.375); }

  Optional<Boolean> getCluster(); // { return Optional.of(false); }

  Optional<Integer> getClusterRadius(); // { return Optional.of(50); }

  Optional<Integer> getClusterMaxZoom(); // { return Optional.of(Integer.valueOf(getMaxzoom()-1)); }

  Optional<Integer> getClusterMinPoints(); // { return Optional.of(2); }

  Optional<Object> getClusterProperties();

  Optional<Object> getFilter(); // { return Optional.empty(); }

  Optional<Boolean> getLineMetrics(); // { return Optional.of(false); }

  Optional<Boolean> getGenerateId(); // { return Optional.of(false); }
}
