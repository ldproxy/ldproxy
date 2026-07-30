/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.immutables.value.Value;

// ToDo Add missing properties
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableInputDescription.Builder.class)
public interface InputDescription extends DescriptionType, SchemaAndOccurrences {

  // ToDo Support references
  enum Passing {
    BY_VALUE
    // BY_REFERENCE
  }

  @Value.Default
  default List<Passing> getValuePassing() {
    return List.of(Passing.BY_VALUE);
  }

  @Override
  @Value.Default
  @Min(0)
  default int getMinOccurs() {
    return 1;
  }

  @Override
  @Value.Default
  @Min(1)
  default int getMaxOccurs() {
    return 1;
  }

  @Value.Check
  default InputDescription validate() {
    if (getMinOccurs() < 0) {
      throw new IllegalStateException("minOccurs (" + getMinOccurs() + ") + must be >= 0");
    }

    if (getMaxOccurs() < 1) {
      throw new IllegalStateException("maxOccurs (" + getMaxOccurs() + ") must be >= 1");
    }

    if (getMinOccurs() > getMaxOccurs()) {
      throw new IllegalStateException(
          "minOccurs (" + getMinOccurs() + ") must be <= maxOccurs (" + getMaxOccurs() + ")");
    }
    return this;
  }
}
