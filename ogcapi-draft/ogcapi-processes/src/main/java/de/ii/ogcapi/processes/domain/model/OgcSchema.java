/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/schema.yaml
 */
@ApiInfo(schemaId = "Schema")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcSchema.Builder.class)
public interface OgcSchema {

  String SCHEMA_REF = "#/components/schemas/Schema";

  enum Formats {
    @JsonProperty("ogc-bbox")
    @JsonAlias("https://www.opengis.net/def/format/ogcapi-processes/0/ogc-bbox")
    OGC_BBOX
  }

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcSchema> FUNNEL =
      (from, into) -> {
        into.putString(from.getType().name(), StandardCharsets.UTF_8);
        from.getFormat().ifPresent(f -> into.putString(f.name(), StandardCharsets.UTF_8));
        from.getRequired().stream()
            .sorted()
            .forEachOrdered(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getProperties().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  Property.FUNNEL.funnel(e.getValue(), into);
                });
      };

  Property.Type getType();

  // Note: This is an addition
  Optional<Formats> getFormat();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getRequired();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, Property> getProperties();
}
