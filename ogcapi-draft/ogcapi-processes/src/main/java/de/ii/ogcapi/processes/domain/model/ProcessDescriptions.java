/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.PageRepresentation;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableProcessDescriptions.Builder.class)
public abstract class ProcessDescriptions extends PageRepresentation {

  public static final String SCHEMA_REF = "#/components/schemas/ProcessDescriptions";

  public abstract List<ProcessDescriptionReduced> getProcesses();

  @JsonAnyGetter
  public abstract Map<String, Object> getExtensions();

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessDescriptions> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        from.getProcesses().stream()
            .sorted(Comparator.comparing(ProcessDescriptionReduced::getId))
            .forEachOrdered(val -> ProcessDescriptionReduced.FUNNEL.funnel(val, into));
        from.getExtensions().keySet().stream()
            .sorted()
            .forEachOrdered(key -> into.putString(key, StandardCharsets.UTF_8));
      };
}
