/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableJsonSchemaObject.Builder.class)
@JsonPropertyOrder({
  "title",
  "description",
  "type",
  "required",
  "properties",
  "patternProperties",
  "additionalProperties"
})
public abstract class JsonSchemaObject extends JsonSchema {

  @Value.Derived
  public String getType() {
    return "object";
  }

  @JsonProperty("required")
  public abstract List<String> getRequired();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public abstract Map<String, JsonSchema> getProperties();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public abstract Map<String, JsonSchema> getPatternProperties();

  public abstract Optional<JsonSchema> getAdditionalProperties();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public abstract List<JsonSchema> getAnyOf();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public abstract List<JsonSchema> getOneOf();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public abstract List<JsonSchema> getAllOf();

  public abstract Optional<JsonSchema> getNot();

  public abstract static class Builder extends JsonSchema.Builder {}

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<JsonSchemaObject> FUNNEL =
      (from, into) -> {
        into.putString(from.getType(), StandardCharsets.UTF_8);
        from.getRequired().stream()
            .sorted()
            .forEachOrdered(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getProperties().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(entry -> JsonSchema.FUNNEL.funnel(entry.getValue(), into));
        from.getPatternProperties().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(entry -> JsonSchema.FUNNEL.funnel(entry.getValue(), into));
        from.getAdditionalProperties().ifPresent(val -> JsonSchema.FUNNEL.funnel(val, into));
        from.getAnyOf().forEach(val -> JsonSchema.FUNNEL.funnel(val, into));
        from.getOneOf().forEach(val -> JsonSchema.FUNNEL.funnel(val, into));
        from.getAllOf().forEach(val -> JsonSchema.FUNNEL.funnel(val, into));
        from.getNot().ifPresent(val -> JsonSchema.FUNNEL.funnel(val, into));
      };
}
