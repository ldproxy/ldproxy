/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.rep;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.processes.domain.model.Execute;
import java.nio.charset.StandardCharsets;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Execute")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcExecute.Builder.class)
public abstract class OgcExecute implements Execute {

  public static final String SCHEMA_REF = "#/components/schemas/Execute";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcExecute> FUNNEL =
      (from, into) -> {
        from.getProcessUri().ifPresent(uri -> into.putString(uri, StandardCharsets.UTF_8));
        // ToDo Funnel rest
      };

  public static OgcExecute of(Execute execute) {
    return new ImmutableOgcExecute.Builder().from(execute).build();
  }
}
