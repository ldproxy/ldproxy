/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.ogc;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import org.immutables.value.Value;

@ApiInfo(schemaId = "ProcessSummary")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableOgcProcessSummary.Builder.class)
@JsonPropertyOrder({
  "id",
  "version",
  "jobControlOptions",
  "title",
  "description",
  "keywords",
  "metadata",
  "links"
})
public abstract class OgcProcessSummary extends ProcessSummaryBase {

  public static final String SCHEMA_REF = "#/components/schemas/ProcessSummary";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcProcessSummary> FUNNEL = ProcessSummaryBase.FUNNEL::funnel;
}
