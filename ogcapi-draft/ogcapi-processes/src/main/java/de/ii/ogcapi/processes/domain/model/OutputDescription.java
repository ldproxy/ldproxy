/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOutputDescription.Builder.class)
public interface OutputDescription extends DescriptionType, SchemaAndOccurrences {

  @Override
  @Value.Default
  default int getMinOccurs() {
    return 1;
  }

  @Override
  @Value.Default
  default int getMaxOccurs() {
    return 1;
  }
}
