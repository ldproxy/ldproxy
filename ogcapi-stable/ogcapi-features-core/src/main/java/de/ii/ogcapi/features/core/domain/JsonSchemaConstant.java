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
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableJsonSchemaConstant.Builder.class)
public abstract class JsonSchemaConstant extends JsonSchema {

  // either a string, number, integer, boolean
  @JsonProperty("const")
  public abstract Object getConstant();

  public abstract static class Builder extends JsonSchema.Builder {}

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<JsonSchemaConstant> FUNNEL =
      (from, into) -> {
        if (from.getConstant() instanceof String) {
          into.putString((String) from.getConstant(), StandardCharsets.UTF_8);
        } else if (from.getConstant() instanceof Integer) {
          into.putInt((Integer) from.getConstant());
        } else if (from.getConstant() instanceof Long) {
          into.putLong((Long) from.getConstant());
        } else if (from.getConstant() instanceof Double) {
          into.putDouble((Double) from.getConstant());
        } else if (from.getConstant() instanceof Float) {
          into.putFloat((Float) from.getConstant());
        } else if (from.getConstant() instanceof Boolean) {
          into.putBoolean((Boolean) from.getConstant());
        }
      };
}
