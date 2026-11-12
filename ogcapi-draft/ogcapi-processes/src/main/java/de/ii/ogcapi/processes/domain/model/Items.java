/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableItems.Builder.class)
public interface Items {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<Items> FUNNEL =
      (from, into) -> {
        into.putString(from.getType().name(), StandardCharsets.UTF_8);
        from.getRequired().stream()
            .sorted()
            .forEachOrdered(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getProperties().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  into.putString(String.valueOf(e.getValue()), StandardCharsets.UTF_8);
                });
      };

  Property.Type getType();

  // the members of an item of the type object
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getRequired();

  // JSON Schemas, not Property: Property has items, and the schema of the API definition is
  // derived from these classes, which does not support a cycle of types
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, Object> getProperties();
}
