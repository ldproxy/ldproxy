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
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface AsyncApiMessage {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<AsyncApiMessage> FUNNEL =
      (from, into) -> {
        from.getName().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getTitle().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getSummary().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getDescription().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        into.putString(from.getContentType(), StandardCharsets.UTF_8);
        from.getPayload().ifPresent(v -> JsonSchema.FUNNEL.funnel(v, into));
        from.getBindings().ifPresent(v -> AsyncApiMessageBindingsMqtt.FUNNEL.funnel(v, into));
      };

  Optional<String> getName();

  Optional<String> getTitle();

  Optional<String> getSummary();

  Optional<String> getDescription();

  @Value.Default
  default String getContentType() {
    return "application/geo+json";
  }

  Optional<JsonSchema> getPayload();

  Optional<AsyncApiMessageBindingsMqtt> getBindings();
}
