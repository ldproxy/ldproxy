/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaFalse;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaArray;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument.VERSION;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SchemaDeriverCollectionProperties extends SchemaDeriverJsonSchema {

  private final List<String> properties;
  private final boolean wildcard;

  public SchemaDeriverCollectionProperties(
      VERSION version,
      Optional<String> schemaUri,
      String label,
      Optional<String> description,
      Map<String, Codelist> codelists,
      List<String> properties) {
    super(version, schemaUri, label, description, codelists, true);
    this.properties = properties;
    this.wildcard = properties.size() == 1 && "*".equals(properties.get(0));
  }

  @Override
  protected void adjustRootSchema(
      FeatureSchema schema,
      Map<String, JsonSchema> properties,
      Map<String, JsonSchema> defs,
      List<String> required,
      JsonSchemaDocument.Builder builder) {
    properties.forEach(
        (propertyName, propertySchema) -> {
          String cleanName = propertyName.replaceAll("\\[]", "");
          if (wildcard || this.properties.contains(cleanName)) {
            builder.putProperties(cleanName, propertySchema);
          }
        });
    builder.additionalProperties(ImmutableJsonSchemaFalse.builder().build());
  }

  @Override
  protected JsonSchema mergeRootSchemas(List<JsonSchema> rootSchemas) {
    JsonSchemaDocument.Builder builder =
        ImmutableJsonSchemaDocument.builder().schema(version.url());

    builder.id(schemaUri).title(label).description(description.orElse(""));

    Map<String, JsonSchema> definitions = new LinkedHashMap<>();
    Map<String, JsonSchema> properties = new LinkedHashMap<>();
    Map<String, JsonSchema> patternProperties = new LinkedHashMap<>();
    Set<String> required = new LinkedHashSet<>();

    rootSchemas.stream()
        .filter(Objects::nonNull)
        .filter(schema -> schema instanceof JsonSchemaDocument)
        .map(schema -> (JsonSchemaDocument) schema)
        .forEach(
            schema -> {
              definitions.putAll(schema.getDefinitions());
              properties.putAll(schema.getProperties());
              patternProperties.putAll(schema.getPatternProperties());
              schema.getAdditionalProperties().ifPresent(builder::additionalProperties);
              required.addAll(schema.getRequired());
            });

    builder.definitions(definitions);
    builder.properties(properties);
    builder.patternProperties(patternProperties);
    builder.required(required);

    return builder.build();
  }

  @Override
  protected JsonSchema deriveValueSchema(FeatureSchema schema) {
    JsonSchema schema2 = super.deriveValueSchema(schema);
    if (Objects.nonNull(schema2)
        && !(schema2 instanceof JsonSchemaArray)
        && (schema.getName().contains("[].") || schema.getName().endsWith("[]"))) {
      schema2 = withArrayWrapper(schema2, schema.getName().endsWith("[]"));
    }
    return schema2;
  }
}
