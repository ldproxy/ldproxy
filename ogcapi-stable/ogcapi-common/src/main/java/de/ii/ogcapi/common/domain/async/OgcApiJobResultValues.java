/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.immutables.value.Value;

@ApiInfo(schemaId = "JobResultValues")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcApiJobResultValues.Builder.class)
public abstract class OgcApiJobResultValues {

  public static final String SCHEMA_REF = "#/components/schemas/JobResultValues";

  @JsonValue
  public abstract Object getInlineOrRefValues();

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcApiJobResultValues> FUNNEL =
      (from, into) -> {
        Object value = from.getInlineOrRefValues();
        if (value instanceof List<?> values) {
          values.stream()
              .map(v -> Objects.toString(v, ""))
              .sorted()
              .forEachOrdered(v -> into.putString(v, StandardCharsets.UTF_8));
        } else {
          into.putString(value.toString(), StandardCharsets.UTF_8);
        }
      };
}
