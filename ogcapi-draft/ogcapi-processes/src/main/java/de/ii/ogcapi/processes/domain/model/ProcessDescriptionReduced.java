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
import de.ii.ogcapi.foundation.domain.PageRepresentationWithId;
import de.ii.ogcapi.processes.domain.model.ProcessDescriptionData.JOB_CONTROL_OPTIONS;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableProcessDescriptionReduced.Builder.class)
public abstract class ProcessDescriptionReduced extends PageRepresentationWithId {

  public static final String SCHEMA_REF = "#/components/schemas/ProcessDescriptionReduced";

  public abstract String getVersion();

  public abstract Optional<List<JOB_CONTROL_OPTIONS>> getJobControlOptions();

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessDescriptionReduced> FUNNEL =
      (from, into) -> {
        PageRepresentationWithId.FUNNEL.funnel(from, into);
        into.putString(from.getVersion(), StandardCharsets.UTF_8);
        into.putString(from.getJobControlOptions().toString(), StandardCharsets.UTF_8);
      };
}
