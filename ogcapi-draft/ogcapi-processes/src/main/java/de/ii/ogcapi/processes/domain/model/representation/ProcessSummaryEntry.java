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
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableProcessSummaryEntry.Builder.class)
public abstract class ProcessSummaryEntry extends ProcessSummaryEntryBase {

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessSummaryEntry> FUNNEL = ProcessSummaryEntryBase.FUNNEL::funnel;
}
