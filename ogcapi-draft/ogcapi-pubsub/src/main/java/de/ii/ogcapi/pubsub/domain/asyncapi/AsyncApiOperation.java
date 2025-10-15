/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.pubsub.domain.asyncapi;

import com.google.common.hash.Funnel;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface AsyncApiOperation {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<AsyncApiOperation> FUNNEL =
      (from, into) -> {
        from.getOperationId().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getSummary().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getDescription().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getBindings().ifPresent(v -> AsyncApiOperationBindingsMqtt.FUNNEL.funnel(v, into));
        from.getParameters().keySet().stream()
            .sorted()
            .forEachOrdered(key -> JsonSchema.FUNNEL.funnel(from.getParameters().get(key), into));
        AsyncApiReference.FUNNEL.funnel(from.getMessage(), into);
      };

  Optional<String> getOperationId();

  Optional<String> getSummary();

  Optional<String> getDescription();

  Optional<AsyncApiOperationBindingsMqtt> getBindings();

  Map<String, JsonSchema> getParameters();

  AsyncApiReference getMessage();
}
