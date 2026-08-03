/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.web;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.processes.domain.model.OgcValues;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Values")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableValuesResponse.Builder.class)
public abstract class ValuesResponse implements OgcValues {

  public static final String SCHEMA_REF = "#/components/schemas/Values";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ValuesResponse> FUNNEL =
      (from, into) -> {
        from.getInlineOrRefValues().stream()
            .map(v -> Objects.toString(v, ""))
            .sorted()
            .forEachOrdered(v -> into.putString(v, StandardCharsets.UTF_8));
      };

  public static ValuesResponse of(OgcValues values) {
    return new ImmutableValuesResponse.Builder().from(values).build();
  }
}
