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

@ApiInfo(schemaId = "Subscriber")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcSubscriber.Builder.class)
/** Optional URIs for callbacks for asynchronous execution. */
public interface OgcSubscriber {

  String SCHEMA_REF = "#/components/schemas/Subscriber";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcSubscriber> FUNNEL =
      (from, into) -> {
        from.successUri().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.inProgressUri().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.failedUri().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
      };

  Optional<String> successUri();

  Optional<String> inProgressUri();

  Optional<String> failedUri();
}
