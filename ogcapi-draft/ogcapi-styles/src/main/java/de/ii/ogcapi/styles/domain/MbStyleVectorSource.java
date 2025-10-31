/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
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

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getTiles();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<Double> getBounds();

  @Value.Default
  default Scheme getScheme() {
    return Scheme.xyz;
  }

  Optional<Number> getMinzoom();

  Optional<Number> getMaxzoom();

  Optional<String> getAttribution();
}
