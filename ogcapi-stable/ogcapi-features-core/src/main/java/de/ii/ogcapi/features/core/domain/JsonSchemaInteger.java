/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableJsonSchemaInteger.Builder.class)
public abstract class JsonSchemaInteger extends JsonSchema {

  @Value.Derived
  public String getType() {
    return "integer";
  }

  public abstract Optional<Long> getMinimum();

  public abstract Optional<Long> getMaximum();

  @JsonProperty("enum")
  public abstract Optional<List<Integer>> getEnums();

  @JsonProperty("x-ogc-unit")
  public abstract Optional<String> getUnit();

  public abstract static class Builder extends JsonSchema.Builder {}

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<JsonSchemaInteger> FUNNEL =
      (from, into) -> {
        into.putString(from.getType(), StandardCharsets.UTF_8);
        from.getMinimum().ifPresent(into::putLong);
        from.getMaximum().ifPresent(into::putLong);
        from.getEnums().ifPresent(enums -> enums.stream().sorted().forEachOrdered(into::putInt));
        from.getUnit().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
      };
}
