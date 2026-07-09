/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.ogc;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableOgcProcessSummary.Builder.class)
public abstract class OgcProcessSummary extends ProcessSummaryBase {

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcProcessSummary> FUNNEL = ProcessSummaryBase.FUNNEL::funnel;
}
