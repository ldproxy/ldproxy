/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.codelist.app;

import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaAllOf;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaArray;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaDocument;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaDocumentV7;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaObject;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaOneOf;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaRef;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaAllOf;
import de.ii.ogcapi.features.core.domain.JsonSchemaArray;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocumentV7;
import de.ii.ogcapi.features.core.domain.JsonSchemaObject;
import de.ii.ogcapi.features.core.domain.JsonSchemaOneOf;
import de.ii.ogcapi.features.core.domain.JsonSchemaRef;
import de.ii.ogcapi.features.core.domain.JsonSchemaVisitor;
import de.ii.ogcapi.foundation.domain.Profile;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MapCodelists implements JsonSchemaVisitor {

  private final List<Profile> profiles;

  public MapCodelists(List<Profile> profiles) {
    this.profiles = profiles;
  }

  @Override
  public JsonSchema visit(JsonSchema schema) {
    if (schema instanceof JsonSchemaAllOf) {
      return new ImmutableJsonSchemaAllOf.Builder()
          .from((JsonSchemaAllOf) schema)
          .allOf(
              ((JsonSchemaAllOf) schema)
                  .getAllOf().stream().map(this::visit).collect(Collectors.toList()))
          .build();
    } else if (schema instanceof JsonSchemaOneOf) {
      return new ImmutableJsonSchemaOneOf.Builder()
          .from((JsonSchemaOneOf) schema)
          .oneOf(
              ((JsonSchemaOneOf) schema)
                  .getOneOf().stream().map(this::visit).collect(Collectors.toList()))
          .build();
    } else if (schema instanceof JsonSchemaArray) {
      return new ImmutableJsonSchemaArray.Builder()
          .from((JsonSchemaArray) schema)
          .items(((JsonSchemaArray) schema).getItems().accept(this))
          .build();
    } else if (schema instanceof JsonSchemaDocumentV7) {
      return ImmutableJsonSchemaDocumentV7.builder()
          .from((JsonSchemaDocumentV7) schema)
          .properties(
              ((JsonSchemaDocumentV7) schema)
                  .getProperties().entrySet().stream()
                      .map(
                          entry ->
                              new SimpleImmutableEntry<>(
                                  entry.getKey(), entry.getValue().accept(this)))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .patternProperties(
              ((JsonSchemaDocumentV7) schema)
                  .getPatternProperties().entrySet().stream()
                      .map(
                          entry ->
                              new SimpleImmutableEntry<>(
                                  entry.getKey(), entry.getValue().accept(this)))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .additionalProperties(
              ((JsonSchemaDocumentV7) schema).getAdditionalProperties().map(ap -> ap.accept(this)))
          .definitions(
              ((JsonSchemaDocumentV7) schema)
                  .getDefinitions().entrySet().stream()
                      .map(
                          entry ->
                              new SimpleImmutableEntry<>(
                                  entry.getKey(), entry.getValue().accept(this)))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .build();
    } else if (schema instanceof JsonSchemaDocument) {
      return ImmutableJsonSchemaDocument.builder()
          .from((JsonSchemaDocument) schema)
          .properties(
              ((JsonSchemaDocument) schema)
                  .getProperties().entrySet().stream()
                      .map(
                          entry ->
                              new SimpleImmutableEntry<>(
                                  entry.getKey(), entry.getValue().accept(this)))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .patternProperties(
              ((JsonSchemaDocument) schema)
                  .getPatternProperties().entrySet().stream()
                      .map(
                          entry ->
                              new SimpleImmutableEntry<>(
                                  entry.getKey(), entry.getValue().accept(this)))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .additionalProperties(
              ((JsonSchemaDocument) schema).getAdditionalProperties().map(ap -> ap.accept(this)))
          .definitions(
              ((JsonSchemaDocument) schema)
                  .getDefinitions().entrySet().stream()
                      .map(
                          entry ->
                              new SimpleImmutableEntry<>(
                                  entry.getKey(), entry.getValue().accept(this)))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .build();
    } else if (schema instanceof JsonSchemaObject) {
      return new ImmutableJsonSchemaObject.Builder()
          .from((JsonSchemaObject) schema)
          .properties(
              ((JsonSchemaObject) schema)
                  .getProperties().entrySet().stream()
                      .map(
                          entry ->
                              new SimpleImmutableEntry<>(
                                  entry.getKey(), entry.getValue().accept(this)))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .patternProperties(
              ((JsonSchemaObject) schema)
                  .getPatternProperties().entrySet().stream()
                      .map(
                          entry ->
                              new SimpleImmutableEntry<>(
                                  entry.getKey(), entry.getValue().accept(this)))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .additionalProperties(
              ((JsonSchemaObject) schema).getAdditionalProperties().map(ap -> ap.accept(this)))
          .build();
    } else if (schema instanceof JsonSchemaRef) {
      JsonSchema def = ((JsonSchemaRef) schema).getDef();
      if (def != null) {
        return new ImmutableJsonSchemaRef.Builder()
            .from((JsonSchemaRef) schema)
            .def(def.accept(this))
            .build();
      }
      return schema;
    }

    if (schema.getCodelistId().isPresent()) {
      String codelistId = schema.getCodelistId().get();
      return profiles.stream()
          .filter(profile -> profile instanceof ProfileCodelist)
          .findFirst()
          .map(p -> ((ProfileCodelist) p).process(schema, codelistId))
          .orElse(schema);
    }

    return schema;
  }
}
