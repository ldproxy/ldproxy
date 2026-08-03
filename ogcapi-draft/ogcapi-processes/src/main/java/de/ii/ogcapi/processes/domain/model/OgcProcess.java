/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/process.yaml
 */
public interface OgcProcess extends OgcProcessSummary {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcProcess> FUNNEL =
      (from, into) -> {
        OgcProcessSummary.FUNNEL.funnel(from, into);
        from.getInputs().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  OgcInputDescription.FUNNEL.funnel(e.getValue(), into);
                });
        from.getOutputs().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  OgcOutputDescription.FUNNEL.funnel(e.getValue(), into);
                });
      };

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, OgcInputDescription> getInputs();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, OgcOutputDescription> getOutputs();
}
