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
import de.ii.ogcapi.processes.domain.model.Values;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Values")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcValues.Builder.class)
public abstract class OgcValues implements Values {

  public static final String SCHEMA_REF = "#/components/schemas/Values";

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcValues> FUNNEL =
      (from, into) -> {
        from.getInlineOrRefValues().stream()
            .sorted()
            .forEachOrdered(v -> into.putInt(v.hashCode()));
        from.getInlineOrRefValue().ifPresent(v -> into.putInt(v.hashCode()));
      };

  public static OgcValues of(Values values) {
    return new ImmutableOgcValues.Builder().from(values).build();
  }
}
