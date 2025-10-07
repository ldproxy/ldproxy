/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableJsonSchemaAnyOf.Builder.class)
public abstract class JsonSchemaAnyOf extends JsonSchema {

  public abstract List<JsonSchema> getAnyOf();

  public abstract static class Builder extends JsonSchema.Builder {}

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<JsonSchemaAnyOf> FUNNEL =
      (from, into) -> {
        from.getAnyOf().forEach(val -> JsonSchema.FUNNEL.funnel(val, into));
      };
}
