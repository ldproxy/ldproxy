/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.ogc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

// ToDo Use correct types for properties
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
        from.getInputs()
            .forEach(
                (k, v) -> {
                  into.putString(k, StandardCharsets.UTF_8);
                  into.putString(v.toString(), StandardCharsets.UTF_8);
                });
        from.getOutputs()
            .ifPresent(
                m ->
                    m.forEach(
                        (k, v) -> {
                          into.putString(k, StandardCharsets.UTF_8);
                          into.putString(v, StandardCharsets.UTF_8);
                        }));
        from.getSubscriber().ifPresent(v -> OgcSubscriber.FUNNEL.funnel(v, into));
      };

  Optional<String> getProcess();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, Object> getInputs();

  // Optional must be used to distinguish between empty and omitted Output
  Optional<Map<String, String>> getOutputs();

  Optional<OgcSubscriber> getSubscriber();
}
