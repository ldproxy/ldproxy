/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.PageRepresentation;
import java.util.Comparator;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
// empty optional values like the title or description are omitted, too
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonDeserialize(as = ImmutableResources.class)
public abstract class Resources extends PageRepresentation {

  public static final String SCHEMA_REF = "#/components/schemas/Resources";

  public abstract List<Resource> getResources();

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<Resources> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        from.getResources().stream()
            .sorted(Comparator.comparing(Resource::getId))
            .forEachOrdered(val -> Resource.FUNNEL.funnel(val, into));
      };
}
