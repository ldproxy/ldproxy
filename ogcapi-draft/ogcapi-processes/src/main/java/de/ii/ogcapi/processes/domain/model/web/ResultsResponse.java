/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.web;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.processes.domain.model.OgcResults;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Results")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableResultsResponse.Builder.class)
public abstract class ResultsResponse implements OgcResults {

  public static final String SCHEMA_REF = "#/components/schemas/Results";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ResultsResponse> FUNNEL =
      (from, into) -> {
        from.getAdditionalProperties().entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  into.putString(Objects.toString(e.getValue(), ""), StandardCharsets.UTF_8);
                });
      };

  public static ResultsResponse of(OgcResults results) {
    return new ImmutableResultsResponse.Builder().from(results).build();
  }
}
