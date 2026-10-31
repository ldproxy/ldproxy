/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.immutables.value.Value;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/inputDescription.yaml
 *
 * <p><code>
 *     ```
 *- The following extensions are missing:
 *  - dataClasses
 *  - dataAccessAPIs
 *  - executionUnitRequirements
 *- References are not supported
 *    ```
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcInputDescription.Builder.class)
public interface OgcInputDescription extends OgcDescriptionType, OgcSchemaAndOccurrences {

  enum Passing {
    BY_VALUE;

    // BY_REFERENCE

    // the value in the specification, e.g. "byValue"
    @JsonValue
    public String toValue() {
      String[] words = name().toLowerCase(Locale.ROOT).split("_");
      StringBuilder value = new StringBuilder(words[0]);
      for (int i = 1; i < words.length; i++) {
        value.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
      }
      return value.toString();
    }

    // accepts the specification value as well as the enum name used in process definitions
    @JsonCreator
    public static Passing fromValue(String value) {
      for (Passing passing : values()) {
        if (passing.name().equalsIgnoreCase(value) || passing.toValue().equals(value)) {
          return passing;
        }
      }
      throw new IllegalArgumentException("Unknown value passing: " + value);
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcInputDescription> FUNNEL =
      (from, into) -> {
        OgcDescriptionType.FUNNEL.funnel(from, into);
        OgcSchema.FUNNEL.funnel(from.getSchema(), into);
        into.putInt(from.getMinOccurs());
        into.putInt(from.getMaxOccurs());
        from.getValuePassing().stream()
            .map(Passing::name)
            .sorted()
            .forEachOrdered(name -> into.putString(name, StandardCharsets.UTF_8));
      };

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
  default OgcInputDescription validate() {
    if (getMinOccurs() < 0) {
      throw new IllegalStateException("minOccurs (" + getMinOccurs() + ") must be >= 0");
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
