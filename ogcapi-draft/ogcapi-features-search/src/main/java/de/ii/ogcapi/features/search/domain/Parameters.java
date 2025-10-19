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
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableParameters.Builder.class)
public abstract class Parameters extends PageRepresentation {

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<Parameters> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        from.getProperties()
            .forEach(
                (name, schema) -> {
                  into.putString(name, StandardCharsets.UTF_8);
                  into.putString(schema.toString(), StandardCharsets.UTF_8);
                });
      };

  @JsonIgnore public static final String SCHEMA_REF = "#/components/schemas/Parameters";

  public final String getType() {
    return "object";
  }

  public abstract List<String> getRequired();

  public abstract Map<String, JsonSchema> getProperties();
}
