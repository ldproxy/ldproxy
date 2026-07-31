/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.foundation.domain.PageRepresentationWithId;
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary;
import java.nio.charset.StandardCharsets;
import org.immutables.value.Value;

@ApiInfo(schemaId = "ProcessSummary")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableProcessSummaryResponse.Builder.class)
@JsonPropertyOrder({
  "id",
  "version",
  "jobControlOptions",
  "title",
  "description",
  "keywords",
  "metadata",
  "links"
})
public abstract class ProcessSummaryResponse extends PageRepresentationWithId
    implements OgcProcessSummary {

  public static final String SCHEMA_REF = "#/components/schemas/ProcessSummary";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessSummaryResponse> FUNNEL =
      (from, into) -> {
        PageRepresentationWithId.FUNNEL.funnel(from, into);
        into.putString(from.getVersion(), StandardCharsets.UTF_8);
        from.getJobControlOptions().stream()
            .map(JobControlOptions::name)
            .sorted()
            .forEachOrdered(name -> into.putString(name, StandardCharsets.UTF_8));
        from.getKeywords().stream()
            .sorted()
            .forEachOrdered(keyword -> into.putString(keyword, StandardCharsets.UTF_8));
      };
}
