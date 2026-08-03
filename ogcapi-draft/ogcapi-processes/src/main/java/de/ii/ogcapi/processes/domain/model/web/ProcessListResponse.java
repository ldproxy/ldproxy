/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.web;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.foundation.domain.PageRepresentation;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.immutables.value.Value;

@ApiInfo(schemaId = "ProcessList")
@Value.Immutable
@JsonDeserialize(builder = ImmutableProcessListResponse.Builder.class)
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonPropertyOrder({"processes", "links"})
public abstract class ProcessListResponse extends PageRepresentation {

  public static final String SCHEMA_REF = "#/components/schemas/ProcessList";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessListResponse> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        from.getProcessList().stream()
            .sorted(Comparator.comparing(ProcessSummaryResponse::getId))
            .forEachOrdered(val -> ProcessSummaryResponse.FUNNEL.funnel(val, into));
        from.getExtensions().entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  into.putString(Objects.toString(e.getValue(), ""), StandardCharsets.UTF_8);
                });
      };

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @JsonProperty("processes")
  public abstract List<ProcessSummaryResponse> getProcessList();

  @JsonAnyGetter
  public abstract Map<String, Object> getExtensions();
}
