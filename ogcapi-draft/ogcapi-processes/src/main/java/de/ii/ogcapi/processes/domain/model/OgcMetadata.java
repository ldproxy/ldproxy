/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/metadata.yaml
 */
@ApiInfo(schemaId = "Metadata")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcMetadata.Builder.class)
@JsonPropertyOrder({"role", "title", "lang", "value"})
public interface OgcMetadata {

  String SCHEMA_REF = "#/components/schemas/Metadata";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcMetadata> FUNNEL =
      (from, into) -> {
        from.getRole().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getTitle().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getLang().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getValue().ifPresent(v -> into.putString(v.toString(), StandardCharsets.UTF_8));
      };

  Optional<String> getRole();

  Optional<String> getTitle();

  Optional<String> getLang();

  Optional<Object> getValue();
}
