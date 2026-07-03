/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.representation;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.foundation.domain.PageRepresentation;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@ApiInfo(schemaId = "ProcessList")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableProcessList.Builder.class)
public abstract class ProcessList extends PageRepresentation {

  public static final String SCHEMA_REF = "#/components/schemas/ProcessList";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessList> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        from.getProcesses().stream()
            .sorted(Comparator.comparing(ProcessSummaryEntry::getId))
            .forEachOrdered(val -> ProcessSummaryEntry.FUNNEL.funnel(val, into));
        from.getExtensions().keySet().stream()
            .sorted()
            .forEachOrdered(key -> into.putString(key, StandardCharsets.UTF_8));
      };

  public abstract List<ProcessSummaryEntry> getProcesses();

  @JsonAnyGetter
  public abstract Map<String, Object> getExtensions();
}
