/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Optional URIs for callbacks for asynchronous execution.
 *
 * <p>See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/subscriber.yaml
 */
@ApiInfo(schemaId = "AsyncSubscriber")
@Value.Immutable
@JsonDeserialize(builder = ImmutableAsyncSubscriber.Builder.class)
public interface AsyncSubscriber {

  String SCHEMA_REF = "#/components/schemas/AsyncSubscriber";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<AsyncSubscriber> FUNNEL =
      (from, into) -> {
        from.getSuccessUri().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getInProgressUri().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getFailedUri().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getMaxRetries().ifPresent(into::putInt);
      };

  Optional<String> getSuccessUri();

  Optional<String> getInProgressUri();

  Optional<String> getFailedUri();

  Optional<Integer> getMaxRetries();
}
