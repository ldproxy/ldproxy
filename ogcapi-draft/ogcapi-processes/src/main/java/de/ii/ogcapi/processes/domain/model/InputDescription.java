/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.immutables.value.Value;

// ToDo Add missing properties
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableInputDescription.Builder.class)
public interface InputDescription extends DescriptionType, SchemaAndOccurrences {

  enum Passing {
    BY_VALUE,
    BY_REFERENCE
  }

  List<Passing> getValuePassing();

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
