/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableJsonSchemaArray.Builder.class)
public abstract class JsonSchemaArray extends JsonSchema {

  @Value.Derived
  public String getType() {
    return "array";
  }

  public abstract Optional<JsonSchema> getItems();

  @JsonInclude(Include.NON_EMPTY)
  public abstract List<JsonSchema> getPrefixItems();

  public abstract Optional<Integer> getMinItems();

  public abstract Optional<Integer> getMaxItems();

  @JsonIgnore
  @Value.Derived
  public JsonSchema getItemSchema() {
    return getItems()
        .orElse(
            !getPrefixItems().isEmpty() ? getPrefixItems().get(0) : JsonSchemaBuildingBlocks.NULL);
  }

  public abstract static class Builder extends JsonSchema.Builder {}

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<JsonSchemaArray> FUNNEL =
      (from, into) -> {
        into.putString(from.getType(), StandardCharsets.UTF_8);
        from.getItems().ifPresent(val -> JsonSchema.FUNNEL.funnel(val, into));
        from.getPrefixItems().forEach(item -> JsonSchema.FUNNEL.funnel(item, into));
        from.getMinItems().ifPresent(into::putInt);
        from.getMaxItems().ifPresent(into::putInt);
      };

  @Value.Check
  protected void check() {
    if (getMinItems().isPresent() && getMaxItems().isPresent()) {
      Preconditions.checkArgument(
          getMinItems().get() <= getMaxItems().get(), "minItems cannot be greater than maxItems");
    }
    Preconditions.checkArgument(
        getItems().isPresent() || !getPrefixItems().isEmpty(),
        "Either items or prefixItems must be present");
  }
}
