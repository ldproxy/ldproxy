/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/execute.yaml
 */
@ApiInfo(schemaId = "Execute")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcExecute.Builder.class)
@JsonPropertyOrder({"process", "inputs", "outputs", "subscriber"})
public interface OgcExecute {

  String SCHEMA_REF = "#/components/schemas/Execute";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcExecute> FUNNEL =
      (from, into) -> {
        from.getProcess().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getInputs().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  into.putString(Objects.toString(e.getValue(), ""), StandardCharsets.UTF_8);
                });
        from.getOutputSelections()
            .ifPresent(
                m ->
                    m.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEachOrdered(
                            e -> {
                              into.putString(e.getKey(), StandardCharsets.UTF_8);
                              OgcFormat.FUNNEL.funnel(e.getValue(), into);
                            }));
        from.getSubscriber().ifPresent(v -> OgcSubscriber.FUNNEL.funnel(v, into));
      };

  Optional<String> getProcess();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, Object> getInputs();

  // Optional must be used to distinguish between empty and omitted Output
  @JsonProperty("outputs")
  Optional<Map<String, OgcFormat>> getOutputSelections();

  Optional<OgcSubscriber> getSubscriber();
}
