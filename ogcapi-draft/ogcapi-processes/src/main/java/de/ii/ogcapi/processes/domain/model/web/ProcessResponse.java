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
import de.ii.ogcapi.processes.domain.model.OgcProcess;
import java.nio.charset.StandardCharsets;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Process")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableProcessResponse.Builder.class)
@JsonPropertyOrder({
  "id",
  "version",
  "jobControlOptions",
  "title",
  "description",
  "keywords",
  "metadata",
  "inputs",
  "outputs",
  "links"
})
public abstract class ProcessResponse extends PageRepresentationWithId implements OgcProcess {

  public static final String SCHEMA_REF = "#/components/schemas/Process";

  // ToDo Add Input / Outputs
  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessResponse> FUNNEL =
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

  public static ProcessResponse of(OgcProcess process) {
    return new ImmutableProcessResponse.Builder().from(process).build();
  }
}
