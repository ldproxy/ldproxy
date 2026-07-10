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
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.processes.domain.model.Results;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Results")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcResults.Builder.class)
public abstract class OgcResults implements Results {

  public static final String SCHEMA_REF = "#/components/schemas/Results";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcResults> FUNNEL =
      (from, into) -> {
        from.getAdditionalProperties().keySet().stream()
            .sorted()
            .forEachOrdered(key -> into.putString(key, StandardCharsets.UTF_8));
        from.getAdditionalProperties().values().stream()
            .sorted(Comparator.comparingInt(Object::hashCode))
            .forEachOrdered(value -> into.putInt(value.hashCode()));
      };

  public static OgcResults of(Results results) {
    return new ImmutableOgcResults.Builder().from(results).build();
  }
}
