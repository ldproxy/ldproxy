/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/format.yaml
 */
@ApiInfo(schemaId = "Format")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcFormat.Builder.class)
public interface OgcFormat {

  String SCHEMA_REF = "#/components/schemas/Format";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcFormat> FUNNEL =
      (from, into) -> {
        from.getMediaType().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getEncoding().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getSchema().ifPresent(v -> OgcSchema.FUNNEL.funnel(v, into));
      };

  Optional<String> getMediaType();

  Optional<String> getEncoding();

  Optional<OgcSchema> getSchema();
}
