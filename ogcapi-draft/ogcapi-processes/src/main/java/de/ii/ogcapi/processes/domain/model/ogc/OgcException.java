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
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Exception")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcException.Builder.class)
public interface OgcException {

  String SCHEMA_REF = "#/components/schemas/Exception";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcException> FUNNEL =
      (from, into) -> {
        into.putString(from.getType(), StandardCharsets.UTF_8);
        from.getTitle().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getStatus().ifPresent(into::putInt);
        from.getDetail().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getInstance().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
      };

  String getType();

  Optional<String> getTitle();

  Optional<Integer> getStatus();

  Optional<String> getDetail();

  Optional<String> getInstance();
}
