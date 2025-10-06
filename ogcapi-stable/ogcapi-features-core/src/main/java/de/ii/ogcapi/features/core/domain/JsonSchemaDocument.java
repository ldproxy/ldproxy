/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonPropertyOrder({
  "$schema",
  "$id",
  "title",
  "description",
  "type",
  "required",
  "properties",
  "patternProperties",
  "additionalProperties",
  "anyOf",
  "oneOf",
  "allOf",
  "not",
  "$defs"
})
public abstract class JsonSchemaDocument extends JsonSchemaObject {
  @JsonProperty("$schema")
  @Value.Default
  public String getSchema() {
    return VERSION.V202012.url();
  }

  @JsonProperty("$id")
  public abstract Optional<String> getId();

  @JsonProperty("$defs")
  public abstract Map<String, JsonSchema> getDefinitions();

  public enum VERSION {
    V202012("https://json-schema.org/draft/2020-12/schema", "$defs");

    public static VERSION current() {
      return V202012;
    }

    private final String url;
    private final String defs;

    VERSION(String url, String defs) {
      this.url = url;
      this.defs = defs;
    }

    public String url() {
      return url;
    }

    public String ref(String name) {
      return String.format("#/%s/%s", defs, name);
    }
  }

  public abstract static class Builder extends JsonSchema.Builder {
    public abstract Builder id(Optional<String> id);

    public abstract Builder definitions(Map<String, ? extends JsonSchema> entries);

    public abstract Builder putDefinitions(String key, JsonSchema value);

    public abstract Builder title(String title);

    public abstract Builder description(String description);

    public abstract Builder name(String name);

    public abstract Builder isRequired(boolean isRequired);

    public abstract Builder required(Iterable<String> elements);

    public abstract Builder addRequired(String... elements);

    public abstract Builder properties(Map<String, ? extends JsonSchema> entries);

    public abstract Builder putProperties(String key, JsonSchema value);

    public abstract Builder patternProperties(Map<String, ? extends JsonSchema> entries);

    public abstract Builder putPatternProperties(String key, JsonSchema value);

    public abstract Builder additionalProperties(JsonSchema value);

    public abstract Builder anyOf(Iterable<? extends JsonSchema> elements);

    public abstract Builder oneOf(Iterable<? extends JsonSchema> elements);

    public abstract Builder allOf(Iterable<? extends JsonSchema> elements);

    public abstract Builder not(JsonSchema value);

    public abstract JsonSchemaDocument build();
  }

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<JsonSchemaDocument> FUNNEL =
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
        into.putString(from.getSchema(), StandardCharsets.UTF_8);
        from.getId().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getDefinitions().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(entry -> JsonSchema.FUNNEL.funnel(entry.getValue(), into));
        from.getAnyOf().stream().forEachOrdered(val -> JsonSchema.FUNNEL.funnel(val, into));
      };
}
