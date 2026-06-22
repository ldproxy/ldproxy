/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.PageRepresentationWithId;
import de.ii.ogcapi.processes.domain.ProcessDescriptionData.JOB_CONTROL_OPTIONS;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableProcessDescriptionLinks.Builder.class)
public abstract class ProcessDescriptionLinks extends PageRepresentationWithId {

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessDescriptionLinks> FUNNEL =
      (from, into) -> {
        PageRepresentationWithId.FUNNEL.funnel(from, into);
        from.getUri().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        into.putString(from.getVersion(), StandardCharsets.UTF_8);
        from.getJobControlOptions()
            .ifPresent(val -> into.putString(val.name(), StandardCharsets.UTF_8));
      };

  public abstract Optional<String> getUri();

  public abstract String getVersion();

  public abstract Optional<JOB_CONTROL_OPTIONS> getJobControlOptions();
}
