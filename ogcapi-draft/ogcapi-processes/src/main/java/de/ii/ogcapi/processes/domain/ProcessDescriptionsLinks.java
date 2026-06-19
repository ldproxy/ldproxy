/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

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
@JsonDeserialize(builder = ImmutableProcessDescriptionsLinks.Builder.class)
public abstract class ProcessDescriptionsLinks extends PageRepresentation {

  public static final String SCHEMA_REF = "#/components/schemas/ProcessDescriptions";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessDescriptionsLinks> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        from.getProcessDescriptions().stream()
            .sorted(Comparator.comparing(ProcessDescriptionLinks::getId))
            .forEachOrdered(val -> ProcessDescriptionLinks.FUNNEL.funnel(val, into));
        from.getExtensions().keySet().stream()
            .sorted()
            .forEachOrdered(key -> into.putString(key, StandardCharsets.UTF_8));
      };

  public abstract List<ProcessDescriptionLinks> getProcessDescriptions();

  @JsonAnyGetter
  public abstract Map<String, Object> getExtensions();
}
