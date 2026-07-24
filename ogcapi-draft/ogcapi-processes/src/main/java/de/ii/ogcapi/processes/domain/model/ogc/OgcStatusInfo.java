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
import de.ii.ogcapi.foundation.domain.PageRepresentation;
import de.ii.ogcapi.processes.domain.model.StatusInfo;
import java.nio.charset.StandardCharsets;
import org.immutables.value.Value;

@ApiInfo(schemaId = "StatusInfo")
@Value.Immutable
@Value.Modifiable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcStatusInfo.Builder.class)
@JsonPropertyOrder({
  "id",
  "processId",
  "status",
  "progress",
  "processingEntityType",
  "profileEntityType",
  "message",
  "exception",
  "created",
  "started",
  "finished",
  "updated",
  "request",
  "title",
  "description",
  "keywords",
  "metadata",
  "links"
})
public abstract class OgcStatusInfo extends PageRepresentation implements StatusInfo {

  public static final String SCHEMA_REF = "#/components/schemas/StatusInfo";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcStatusInfo> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        into.putString(from.getId(), StandardCharsets.UTF_8);
        into.putString(from.getProcessId(), StandardCharsets.UTF_8);
        from.getRequest().ifPresent(v -> OgcExecute.FUNNEL.funnel(v, into));
        into.putString(from.getStatus().name(), StandardCharsets.UTF_8);
        from.getMessage().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getCreated().ifPresent(v -> into.putLong(v.toEpochMilli()));
        from.getStarted().ifPresent(v -> into.putLong(v.toEpochMilli()));
        from.getFinished().ifPresent(v -> into.putLong(v.toEpochMilli()));
        from.getUpdated().ifPresent(v -> into.putLong(v.toEpochMilli()));
        from.getProgress().ifPresent(into::putInt);
      };

  public static OgcStatusInfo of(StatusInfo statusInfo) {
    return new ImmutableOgcStatusInfo.Builder().from(statusInfo).build();
  }
}
