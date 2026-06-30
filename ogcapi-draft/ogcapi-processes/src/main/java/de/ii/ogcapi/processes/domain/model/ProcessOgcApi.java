/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.foundation.domain.PageRepresentationWithId;
import de.ii.ogcapi.processes.domain.model.ProcessData.JOB_CONTROL_OPTIONS;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableProcessOgcApi.Builder.class)
@ApiInfo(schemaId = "ProcessDescription")
public abstract class ProcessOgcApi extends PageRepresentationWithId implements Process {

  public static ProcessOgcApi of(Process process) {
    return new ImmutableProcessOgcApi.Builder().from(process).build();
  }

  public static final String SCHEMA_REF = "#/components/schemas/ProcessDescription";

  public abstract String getVersion();

  public abstract Optional<List<String>> getKeywords();

  public abstract Optional<List<JOB_CONTROL_OPTIONS>> getJobControlOptions();

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessOgcApi> FUNNEL =
      (from, into) -> {
        PageRepresentationWithId.FUNNEL.funnel(from, into);
        into.putString(from.getVersion(), StandardCharsets.UTF_8);
        into.putString(from.getJobControlOptions().toString(), StandardCharsets.UTF_8);
        from.getKeywords()
            .ifPresent(l -> l.forEach(keyword -> into.putString(keyword, StandardCharsets.UTF_8)));
      };
}
