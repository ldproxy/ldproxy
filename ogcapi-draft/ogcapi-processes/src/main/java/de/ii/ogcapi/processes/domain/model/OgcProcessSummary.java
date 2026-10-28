/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/processSummary.yaml
 */
public interface OgcProcessSummary extends OgcDescriptionType {

  enum JobControlOptions {
    SYNC_EXECUTE,
    ASYNC_EXECUTE,
    DISMISS;

    @JsonValue
    public String toValue() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    // accepts the spec value as well as the enum name used in process definitions
    @JsonCreator
    public static JobControlOptions fromValue(String value) {
      return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcProcessSummary> FUNNEL =
      (from, into) -> {
        OgcDescriptionType.FUNNEL.funnel(from, into);
        into.putString(from.getVersion(), StandardCharsets.UTF_8);
        from.getJobControlOptions().stream()
            .map(JobControlOptions::name)
            .sorted()
            .forEachOrdered(name -> into.putString(name, StandardCharsets.UTF_8));
      };

  String getId();

  String getVersion();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<JobControlOptions> getJobControlOptions();
}
