/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument.VERSION;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SchemaDeriverFeatures extends SchemaDeriverJsonSchema {

  public SchemaDeriverFeatures(
      VERSION version,
      Optional<String> schemaUri,
      String label,
      Optional<String> description,
      Map<String, Codelist> codelists) {
    super(version, schemaUri, label, description, codelists, true);
  }

  @Override
  protected void adjustObjectSchema(
      FeatureSchema schema,
      Map<String, JsonSchema> properties,
      List<String> required,
      ImmutableJsonSchemaObject.Builder builder) {

    builder.properties(properties);
    if (!required.isEmpty()) {
      if (schema.isEmbeddedFeature()) {
        builder.required(
            required.stream()
                .map(this::getNameWithoutRole)
                .collect(Collectors.toUnmodifiableList()));
      } else {
        builder.required(required);
      }
    }
  }

  @Override
  protected void adjustRootSchema(
      FeatureSchema schema,
      Map<String, JsonSchema> properties,
      Map<String, JsonSchema> defs,
      List<String> required,
      JsonSchemaDocument.Builder builder) {

    defs.forEach(
        (name, def) -> {
          if (Objects.equals("Link", name)) {
            builder.putDefinitions("Link", JsonSchemaBuildingBlocks.LINK_JSON);
          } else {
            builder.putDefinitions(name, def);
          }
        });

    List<String> requiredProperties =
        required.stream().map(this::getNameWithoutRole).collect(Collectors.toUnmodifiableList());
    if (!requiredProperties.isEmpty()) {
      builder.required(requiredProperties);
    }
    withoutFlattenedArrays(properties).forEach(builder::putProperties);
    builder.patternProperties(onlyFlattenedArraysAsPatterns(properties));
  }

  private Map<String, JsonSchema> onlyFlattenedArraysAsPatterns(
      Map<String, JsonSchema> properties) {
    return properties.entrySet().stream()
        .filter(entry -> entry.getValue().getName().filter(name -> name.contains("[]")).isPresent())
        .map(
            entry ->
                new SimpleImmutableEntry<>(
                    flattenedArrayNameAsPattern(entry.getKey()), entry.getValue()))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String flattenedArrayNameAsPattern(String name) {
    return "^" + name.replace("[]", "\\.\\d+") + "$";
  }

  private Map<String, JsonSchema> withoutFlattenedArrays(Map<String, JsonSchema> properties) {
    return properties.entrySet().stream()
        .filter(
            entry -> entry.getValue().getName().filter(name -> !name.contains("[]")).isPresent())
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
