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
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableProperty.Builder.class)
public interface Property {

  // Limitation: This implementation does not allow null
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

  @SuppressWarnings("UnstableApiUsage")
  Funnel<Property> FUNNEL =
      (from, into) -> {
        from.getType().ifPresent(t -> into.putString(t.name(), StandardCharsets.UTF_8));
        from.getTitle().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getDescription().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getFormat().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getContentMediaType().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getMaximum().ifPresent(v -> into.putDouble(v));
        from.getExclusiveMaximum().ifPresent(v -> into.putDouble(v));
        from.getMinimum().ifPresent(v -> into.putDouble(v));
        from.getExclusiveMinimum().ifPresent(v -> into.putDouble(v));
        from.getPattern().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getMaxItems().ifPresent(v -> into.putInt(v));
        into.putInt(from.getMinItems());
        from.getEnum().stream()
            .sorted()
            .forEachOrdered(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getItems().ifPresent(v -> Items.FUNNEL.funnel(v, into));
        from.getXOgcDefinition().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getXOgcUnit().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getXOgcUnitLang().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
      };

  Optional<String> getTitle();

  Optional<String> getDescription();

  Optional<Type> getType();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getEnum();

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

  // Note: This is an addition
  Optional<Items> getItems();

  @JsonProperty("x-ogc-definition")
  Optional<String> getXOgcDefinition();

  @JsonProperty("x-ogc-unit")
  Optional<String> getXOgcUnit();

  @JsonProperty("x-ogc-unitLang")
  Optional<String> getXOgcUnitLang();
}
