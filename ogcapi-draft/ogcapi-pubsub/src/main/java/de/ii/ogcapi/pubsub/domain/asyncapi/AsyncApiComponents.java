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
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
public interface AsyncApiComponents {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<AsyncApiComponents> FUNNEL =
      (from, into) -> {
        from.getMessages().keySet().stream()
            .sorted()
            .forEachOrdered(
                key -> AsyncApiMessage.FUNNEL.funnel(from.getMessages().get(key), into));
        from.getSchemas().keySet().stream()
            .sorted()
            .forEachOrdered(key -> JsonSchema.FUNNEL.funnel(from.getSchemas().get(key), into));
        from.getSecuritySchemes().keySet().stream()
            .sorted()
            .forEachOrdered(
                key -> AsyncApiSecurity.FUNNEL.funnel(from.getSecuritySchemes().get(key), into));
      };

  Map<String, AsyncApiMessage> getMessages();

  Map<String, JsonSchema> getSchemas();

  Map<String, AsyncApiSecurity> getSecuritySchemes();
}
