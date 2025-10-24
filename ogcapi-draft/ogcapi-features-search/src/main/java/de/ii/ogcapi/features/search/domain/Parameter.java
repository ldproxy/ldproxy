/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.PageRepresentation;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import java.nio.charset.StandardCharsets;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableParameter.Builder.class)
public abstract class Parameter extends PageRepresentation {

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<Parameter> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        into.putString(from.getSchema().toString(), StandardCharsets.UTF_8);
      };

  @JsonIgnore public static final String SCHEMA_REF = "#/components/schemas/Parameter";

  public abstract JsonSchema getSchema();
}
