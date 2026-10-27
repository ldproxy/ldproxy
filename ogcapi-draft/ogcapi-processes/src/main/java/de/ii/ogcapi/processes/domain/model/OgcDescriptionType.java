/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/descriptionType.yaml
 */
@ApiInfo(schemaId = "DescriptionType")
@JsonPropertyOrder({"title", "description", "keywords", "metadata"})
public interface OgcDescriptionType {

  String SCHEMA_REF = "#/components/schemas/DescriptionType";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcDescriptionType> FUNNEL =
      (from, into) -> {
        from.getTitle().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getDescription().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getKeywords().stream()
            .sorted()
            .forEachOrdered(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getMetadata().forEach(v -> OgcMetadata.FUNNEL.funnel(v, into));
      };

  Optional<String> getTitle();

  Optional<String> getDescription();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getKeywords();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<OgcMetadata> getMetadata();
}
