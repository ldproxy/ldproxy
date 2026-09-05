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
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.processes.domain.model.Property.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Note: This implementation includes many additions to the schema to support the examples in the
 * draft, as the examples do not match the underlying model!
 *
 * <p>See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/schema.yaml
 */
@ApiInfo(schemaId = "Schema")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcSchema.Builder.class)
public interface OgcSchema {

  String SCHEMA_REF = "#/components/schemas/Schema";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcSchema> FUNNEL =
      (from, into) -> {
        from.getTitle().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getDescription().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        into.putString(from.getType().name(), StandardCharsets.UTF_8);
        from.getFormat().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getContentMediaType().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getMaximum().ifPresent(v -> into.putDouble(v));
        from.getExclusiveMaximum().ifPresent(v -> into.putDouble(v));
        from.getMinimum().ifPresent(v -> into.putDouble(v));
        from.getExclusiveMinimum().ifPresent(v -> into.putDouble(v));
        from.getPattern().ifPresent(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getMaxItems().ifPresent(v -> into.putInt(v));
        into.putInt(from.getMinItems());
        from.getEnum().stream()
            .sorted()
            .forEachOrdered(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getItems().ifPresent(v -> Items.FUNNEL.funnel(v, into));
        from.getRequired().stream()
            .sorted()
            .forEachOrdered(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getProperties().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(
                e -> {
                  into.putString(e.getKey(), StandardCharsets.UTF_8);
                  Property.FUNNEL.funnel(e.getValue(), into);
                });
      };

  Optional<String> getTitle();

  Optional<String> getDescription();

  Type getType();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getEnum();

  Optional<String> getFormat();

  Optional<String> getContentMediaType();

  Optional<Double> getMaximum();

  Optional<Double> getExclusiveMaximum();

  Optional<Double> getMinimum();

  Optional<Double> getExclusiveMinimum();

  Optional<String> getPattern();

  Optional<Integer> getMaxItems();

  @Value.Default
  default int getMinItems() {
    return 0;
  }

  Optional<Items> getItems();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getRequired();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, Property> getProperties();
}
