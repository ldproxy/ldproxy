/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.processes.domain.model.Schema.Type;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutablePropertySchema.Builder.class)
public interface PropertySchema {
  // Note: This implementation does not allow null
  enum Type {
    @JsonProperty("array")
    ARRAY,
    @JsonProperty("boolean")
    BOOLEAN,
    @JsonProperty("integer")
    INTEGER,
    @JsonProperty("number")
    NUMBER,
    @JsonProperty("object")
    OBJECT,
    @JsonProperty("string")
    STRING
  }

  Optional<String> getTitle();

  Optional<String> getDescription();

  Optional<Type> getType();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<Object> getEnum();

  Optional<String> getFormat();

  Optional<String> getContentMediaType();

  Optional<Double> getMaximum();

  Optional<Double> getExclusiveMaximum();

  Optional<Double> getMinimum();

  Optional<Double> getExclusiveMinimum();

  Optional<String> getPattern();

  Optional<Integer> getMaxItems();

  @Value.Default
  default int getMinItems() {
    return 0;
  }

  // Note: This is an addition to the draft
  Optional<Items> getItems();

  @JsonProperty("x-ogc-definition")
  Optional<String> getXOgcDefinition();

  @JsonProperty("x-ogc-unit")
  Optional<String> getXOgcUnit();

  @JsonProperty("x-ogc-unitLang")
  Optional<String> getXOgcUnitLang();
}
