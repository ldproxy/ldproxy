/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.immutables.value.Value;

@ApiInfo(schemaId = "JobResults")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcApiJobResults.Builder.class)
public abstract class OgcApiJobResults {

  public static final String SCHEMA_REF = "#/components/schemas/JobResults";

  @JsonAnyGetter
  public abstract Map<String, Object> getAdditionalProperties();

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcApiJobResults> FUNNEL =
      (from, into) -> {
        from.getAdditionalProperties().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  into.putString(Objects.toString(e.getValue(), ""), StandardCharsets.UTF_8);
                });
      };
}
