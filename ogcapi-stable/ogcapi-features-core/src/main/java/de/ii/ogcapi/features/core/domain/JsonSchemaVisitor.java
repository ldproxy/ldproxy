/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaArray.Builder;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public interface JsonSchemaVisitor {

  JsonSchema visit(JsonSchema schema);

  default JsonSchema visitProperties(JsonSchema schema) {
    if (schema instanceof JsonSchemaAllOf allOf) {
      return new ImmutableJsonSchemaAllOf.Builder()
          .from(allOf)
          .allOf(
              allOf.getAllOf().stream()
                  .map(option -> option.accept(this))
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()))
          .build();
    } else if (schema instanceof JsonSchemaOneOf oneOf) {
      return new ImmutableJsonSchemaOneOf.Builder()
          .from(oneOf)
          .oneOf(
              oneOf.getOneOf().stream()
                  .map(option -> option.accept(this))
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()))
          .build();
    } else if (schema instanceof JsonSchemaArray array) {
      ImmutableJsonSchemaArray.Builder builder = new Builder().from(array);
      array.getItems().ifPresent(items -> builder.items(Optional.ofNullable(items.accept(this))));
      if (!array.getPrefixItems().isEmpty()) {
        builder.prefixItems(
            array.getPrefixItems().stream()
                .map(item -> item.accept(this))
                .filter(Objects::nonNull)
                .toList());
      }
      return builder.build();
    } else if (schema instanceof JsonSchemaDocument document) {
      return ImmutableJsonSchemaDocument.builder()
          .from(document)
          .properties(processSchemaMap(document.getProperties()))
          .patternProperties(processSchemaMap(document.getPatternProperties()))
          .additionalProperties(
              document
                  .getAdditionalProperties()
                  .flatMap(ap -> Optional.ofNullable(ap.accept(this))))
          .definitions(processSchemaMap(document.getDefinitions()))
          .build();
    } else if (schema instanceof JsonSchemaObject object) {
      return new ImmutableJsonSchemaObject.Builder()
          .from(object)
          .properties(processSchemaMap(object.getProperties()))
          .patternProperties(processSchemaMap(object.getPatternProperties()))
          .additionalProperties(
              object.getAdditionalProperties().flatMap(ap -> Optional.ofNullable(ap.accept(this))))
          .build();
    } else if (schema instanceof JsonSchemaRef ref) {
      JsonSchema def = ref.getDef();
      if (def != null) {
        return new ImmutableJsonSchemaRef.Builder().from(ref).def(def.accept(this)).build();
      }
    }

    return schema;
  }

  default Map<String, JsonSchema> processSchemaMap(Map<String, JsonSchema> properties) {
    return properties.entrySet().stream()
        .map(
            entry -> {
              JsonSchema mapped = entry.getValue().accept(this);
              if (mapped == null) {
                return null;
              }
              return new SimpleImmutableEntry<>(entry.getKey(), entry.getValue().accept(this));
            })
        .filter(Objects::nonNull)
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
