/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.representation;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.processes.domain.model.Process;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Process")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableProcessEntry.Builder.class)
public abstract class ProcessEntry extends ProcessSummaryEntryBase
    implements de.ii.ogcapi.processes.domain.model.Process {

  public static final String SCHEMA_REF = "#/components/schemas/Process";

  // ToDo Add Input / Outputs once implemented
  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessEntry> FUNNEL = ProcessSummaryEntryBase.FUNNEL::funnel;

  public static ProcessEntry of(Process process) {
    return new ImmutableProcessEntry.Builder().from(process).build();
  }
}
