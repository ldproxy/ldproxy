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
import de.ii.xtraplatform.features.domain.ImmutableSchemaConstraints;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import de.ii.xtraplatform.features.domain.SchemaDeriver;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SchemaDeriverJsonSchema extends SchemaDeriver<JsonSchema> {

  protected final VERSION version;
  protected final Optional<String> schemaUri;
  protected final String label;
  protected final Optional<String> description;
  private final boolean useCodelistKeys;

  public SchemaDeriverJsonSchema(
      VERSION version,
      Optional<String> schemaUri,
      String label,
      Optional<String> description,
      Map<String, Codelist> codelists,
      boolean useCodelistKeys) {
    super(codelists);
    this.version = version;
    this.schemaUri = schemaUri;
    this.label = label;
    this.description = description;
    this.useCodelistKeys = useCodelistKeys;
  }

  @Override
  protected Optional<String> getPropertyName(JsonSchema property) {
    return property.getName();
  }

  @Override
  protected boolean isPropertyRequired(JsonSchema property) {
    return property.isRequired();
  }

  @Override
  protected Map<String, JsonSchema> getNestedProperties(JsonSchema property) {
    return property instanceof JsonSchemaObject
        ? ((JsonSchemaObject) property).getProperties()
        : ImmutableMap.of();
  }

  @Override
  protected JsonSchema buildRootSchema(
      FeatureSchema schema,
      Map<String, JsonSchema> properties,
      Map<String, JsonSchema> definitions,
      List<String> requiredProperties) {

    JsonSchemaDocument.Builder builder =
        version == VERSION.V7
            ? ImmutableJsonSchemaDocumentV7.builder()
            : ImmutableJsonSchemaDocument.builder().schema(version.url());

    builder
        .id(schemaUri)
        .title(label)
        .description(description.orElse(schema.getDescription().orElse("")));

    adjustRootSchema(schema, properties, definitions, requiredProperties, builder);

    return builder.build();
  }

  @Override
  protected JsonSchema mergeRootSchemas(List<JsonSchema> rootSchemas) {
    JsonSchemaDocument.Builder builder =
        version == VERSION.V7
            ? ImmutableJsonSchemaDocumentV7.builder()
            : ImmutableJsonSchemaDocument.builder().schema(version.url());

    builder.id(schemaUri).title(label).description(description.orElse(""));

    Map<String, JsonSchema> definitions = new LinkedHashMap<>();
    List<JsonSchema> schemas = new ArrayList<>();

    rootSchemas.stream()
        .filter(Objects::nonNull)
        .filter(schema -> schema instanceof JsonSchemaDocument)
        .map(schema -> (JsonSchemaDocument) schema)
        .forEach(
            schema -> {
              definitions.putAll(schema.getDefinitions());
              schemas.add(
                  new ImmutableJsonSchemaObject.Builder()
                      .from(schema)
                      .title(Optional.empty())
                      .description(Optional.empty())
                      .build());
            });

    builder.definitions(definitions);
    builder.anyOf(schemas);

    return builder.build();
  }

  protected void adjustRootSchema(
      FeatureSchema schema,
      Map<String, JsonSchema> properties,
      Map<String, JsonSchema> definitions,
      List<String> requiredProperties,
      JsonSchemaDocument.Builder rootBuilder) {}

  @Override
  protected Stream<JsonSchema> extractDefinitions(Stream<JsonSchema> properties) {
    return properties
        .filter(property -> Objects.nonNull(property) && property.getName().isPresent())
        .map(
            property ->
                property instanceof JsonSchemaArray
                    ? ((JsonSchemaArray) property).getItems()
                    : property)
        .flatMap(
            property ->
                property instanceof JsonSchemaOneOf
                    ? ((JsonSchemaOneOf) property).getOneOf().stream()
                    : Stream.of(property))
        .filter(property -> property instanceof JsonSchemaRef && property.getName().isPresent())
        .filter(
            ref ->
                Objects.nonNull(((JsonSchemaRef) ref).getDef())
                    && ((JsonSchemaRef) ref).getDef().getName().isPresent())
        .map(ref -> ((JsonSchemaRef) ref).getDef())
        .flatMap(
            def ->
                def instanceof JsonSchemaObject
                    ? Stream.concat(
                        Stream.of(def),
                        extractDefinitions(
                            ((JsonSchemaObject) def).getProperties().values().stream()))
                    : Stream.of(def));
  }

  @Override
  protected JsonSchema buildObjectSchema(
      FeatureSchema schema, Map<String, JsonSchema> properties, List<String> requiredProperties) {
    ImmutableJsonSchemaObject.Builder builder =
        new ImmutableJsonSchemaObject.Builder()
            .name(schema.getName())
            .title(schema.getLabel())
            .description(schema.getDescription());

    adjustObjectSchema(schema, properties, requiredProperties, builder);

    return builder.build();
  }

  protected void adjustObjectSchema(
      FeatureSchema schema,
      Map<String, JsonSchema> properties,
      List<String> requiredProperties,
      ImmutableJsonSchemaObject.Builder objectBuilder) {
    objectBuilder.properties(properties);
    if (!requiredProperties.isEmpty()) {
      objectBuilder.required(requiredProperties);
    }
  }

  @Override
  protected JsonSchema getSchemaForLiteralType(
      Type type,
      Optional<String> label,
      Optional<String> description,
      Optional<String> unit,
      Optional<String> role,
      Optional<String> refCollectionId,
      Optional<String> refUriTemplate,
      Optional<String> codelistId) {
    switch (type) {
      case INTEGER:
        return new ImmutableJsonSchemaInteger.Builder()
            .title(label)
            .description(description)
            .unit(unit)
            .role(role)
            .refCollectionId(refCollectionId)
            .refUriTemplate(refUriTemplate)
            .codelistId(codelistId)
            .build();
      case FLOAT:
        return new ImmutableJsonSchemaNumber.Builder()
            .title(label)
            .description(description)
            .unit(unit)
            .role(role)
            .build();
      case BOOLEAN:
        return new ImmutableJsonSchemaBoolean.Builder()
            .title(label)
            .description(description)
            .role(role)
            .build();
      case DATETIME:
        return new ImmutableJsonSchemaString.Builder()
            // validators will ignore this information as it isn't a well-known format value
            .format("date-time")
            .title(label)
            .description(description)
            .role(role)
            .build();
      case DATE:
        return new ImmutableJsonSchemaString.Builder()
            // validators will ignore this information as it isn't a well-known format value
            .format("date")
            .title(label)
            .description(description)
            .role(role)
            .build();
      case STRING:
      default:
        return new ImmutableJsonSchemaString.Builder()
            .title(label)
            .description(description)
            .unit(unit)
            .role(role)
            .refCollectionId(refCollectionId)
            .refUriTemplate(refUriTemplate)
            .codelistId(codelistId)
            .build();
    }
  }

  @Override
  protected JsonSchema getSchemaForGeometry(
      GeometryType geometryType,
      Optional<String> title,
      Optional<String> description,
      Optional<String> role) {
    JsonSchema jsonSchema =
        switch (geometryType) {
          case POINT -> JsonSchemaBuildingBlocks.POINT;
          case MULTI_POINT -> JsonSchemaBuildingBlocks.MULTI_POINT;
          case LINE_STRING -> JsonSchemaBuildingBlocks.LINE_STRING;
          case MULTI_LINE_STRING -> JsonSchemaBuildingBlocks.MULTI_LINE_STRING;
          case POLYGON -> JsonSchemaBuildingBlocks.POLYGON;
          case MULTI_POLYGON -> JsonSchemaBuildingBlocks.MULTI_POLYGON;
          case GEOMETRY_COLLECTION -> JsonSchemaBuildingBlocks.GEOMETRY_COLLECTION;
          case CIRCULAR_STRING -> JsonSchemaBuildingBlocks.CIRCULAR_STRING;
          case COMPOUND_CURVE -> JsonSchemaBuildingBlocks.COMPOUND_CURVE;
          case MULTI_CURVE -> JsonSchemaBuildingBlocks.MULTI_CURVE;
          case CURVE_POLYGON -> JsonSchemaBuildingBlocks.CURVE_POLYGON;
          case POLYHEDRAL_SURFACE -> JsonSchemaBuildingBlocks.POLYHEDRAL_SURFACE;
          case MULTI_SURFACE -> JsonSchemaBuildingBlocks.MULTI_SURFACE;
          default -> JsonSchemaBuildingBlocks.GEOMETRY;
        };
    return new ImmutableJsonSchemaGeometry.Builder()
        .from(jsonSchema)
        .title(title)
        .description(description)
        .role(role)
        .build();
  }

  @Override
  protected JsonSchema withPropertySeq(JsonSchema jsonSchema, int propertySeq) {
    return modify(jsonSchema, builder -> builder.propertySeq(propertySeq));
  }

  @Override
  protected JsonSchema withName(JsonSchema jsonSchema, String propertyName) {
    return modify(jsonSchema, builder -> builder.name(propertyName));
  }

  @Override
  protected JsonSchema withRequired(JsonSchema jsonSchema) {
    return modify(jsonSchema, builder -> builder.isRequired(true));
  }

  @Override
  protected JsonSchema withReadOnly(JsonSchema jsonSchema) {
    return modify(jsonSchema, builder -> builder.readOnly(Optional.of(true)));
  }

  @Override
  protected JsonSchema withWriteOnly(JsonSchema jsonSchema) {
    return modify(jsonSchema, builder -> builder.writeOnly(Optional.of(true)));
  }

  protected JsonSchema modify(JsonSchema jsonSchema, Consumer<JsonSchema.Builder> modifier) {
    JsonSchema.Builder builder = null;

    if (jsonSchema instanceof JsonSchemaObject) {
      builder = new ImmutableJsonSchemaObject.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaOneOf) {
      builder = new ImmutableJsonSchemaOneOf.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaArray) {
      builder = new ImmutableJsonSchemaArray.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaRef) {
      builder = new ImmutableJsonSchemaRef.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaNull) {
      builder = new ImmutableJsonSchemaNull.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaInteger) {
      builder = new ImmutableJsonSchemaInteger.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaBoolean) {
      builder = new ImmutableJsonSchemaBoolean.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaNumber) {
      builder = new ImmutableJsonSchemaNumber.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaString) {
      builder = new ImmutableJsonSchemaString.Builder().from(jsonSchema);
    } else if (jsonSchema instanceof JsonSchemaGeometry) {
      builder = new ImmutableJsonSchemaGeometry.Builder().from(jsonSchema);
    }

    if (Objects.nonNull(builder)) {
      modifier.accept(builder);
      return builder.build();
    }

    return jsonSchema;
  }

  protected JsonSchema withConstraints(
      JsonSchema schema,
      SchemaConstraints constraints,
      FeatureSchema property,
      Map<String, Codelist> codelists) {
    JsonSchema result = schema;
    if (schema instanceof JsonSchemaArray) {
      result =
          new ImmutableJsonSchemaArray.Builder()
              .from(schema)
              .items(
                  withConstraints(
                      ((JsonSchemaArray) schema).getItems(),
                      new ImmutableSchemaConstraints.Builder()
                          .from(constraints)
                          .required(false)
                          .build(),
                      property,
                      codelists))
              .minItems(constraints.getMinOccurrence())
              .maxItems(constraints.getMaxOccurrence())
              .build();

      if (constraints.getRequired().orElse(false) || constraints.getMinOccurrence().orElse(0) > 0) {
        result = withRequired(result);
      }

      return result;
    }

    if (constraints.getRequired().orElse(false)) {
      result = withRequired(result);
    }

    if (!constraints.getEnumValues().isEmpty()) {
      // if enum is specified in the configuration, it wins over codelist
      boolean string =
          property.isArray()
              ? property.getValueType().orElse(SchemaBase.Type.UNKNOWN) != SchemaBase.Type.INTEGER
              : property.getType() != SchemaBase.Type.INTEGER;
      result =
          string
              ? new ImmutableJsonSchemaString.Builder()
                  .from(result)
                  .enums(constraints.getEnumValues())
                  .build()
              : new ImmutableJsonSchemaInteger.Builder()
                  .from(result)
                  .enums(
                      constraints.getEnumValues().stream()
                          .map(val -> Integer.parseInt(val))
                          .collect(Collectors.toList()))
                  .build();
    } else if (constraints.getCodelist().isPresent()) {
      Optional<Codelist> codelist =
          Optional.ofNullable(codelists.get(constraints.getCodelist().get()));
      if (codelist.isPresent() && !codelist.get().getFallback().isPresent()) {
        boolean string =
            property.isArray()
                ? property.getValueType().orElse(SchemaBase.Type.UNKNOWN) != SchemaBase.Type.INTEGER
                : property.getType() != SchemaBase.Type.INTEGER;
        Collection<String> values =
            useCodelistKeys
                ? codelist.get().getEntries().keySet()
                : codelist.get().getEntries().values();
        result =
            string
                ? new ImmutableJsonSchemaString.Builder()
                    .from(result)
                    .enums(Optional.of(values.stream().toList()))
                    .build()
                : new ImmutableJsonSchemaInteger.Builder()
                    .from(result)
                    .enums(
                        values.isEmpty()
                            ? Optional.empty()
                            : Optional.of(
                                values.stream()
                                    .flatMap(
                                        val -> {
                                          try {
                                            return Stream.of(Integer.valueOf(val));
                                          } catch (Throwable e) {
                                            return Stream.empty();
                                          }
                                        })
                                    .sorted()
                                    .collect(Collectors.toList())))
                    .build();
      }
    }
    if (constraints.getRegex().isPresent() && result instanceof ImmutableJsonSchemaString) {
      result =
          new ImmutableJsonSchemaString.Builder()
              .from(result)
              .pattern(constraints.getRegex().get())
              .build();
    }
    if (constraints.getMin().isPresent() || constraints.getMax().isPresent()) {
      if (result instanceof ImmutableJsonSchemaInteger) {
        ImmutableJsonSchemaInteger.Builder builder =
            new ImmutableJsonSchemaInteger.Builder().from(result);
        if (constraints.getMin().isPresent())
          builder.minimum(Math.round(constraints.getMin().get()));
        if (constraints.getMax().isPresent())
          builder.maximum(Math.round(constraints.getMax().get()));
        result = builder.build();
      } else if (result instanceof ImmutableJsonSchemaNumber) {
        ImmutableJsonSchemaNumber.Builder builder =
            new ImmutableJsonSchemaNumber.Builder().from(result);
        if (constraints.getMin().isPresent()) builder.minimum(constraints.getMin().get());
        if (constraints.getMax().isPresent()) builder.maximum(constraints.getMax().get());
        result = builder.build();
      }
    }

    return result;
  }

  @Override
  protected JsonSchema withRefWrapper(JsonSchema schema, String objectType) {
    return new ImmutableJsonSchemaRef.Builder()
        .name(schema.getName())
        .ref(String.format("#/%s/%s", version == VERSION.V7 ? "definitions" : "$defs", objectType))
        .def(new ImmutableJsonSchemaObject.Builder().from(schema).name(objectType).build())
        .build();
  }

  @Override
  protected JsonSchema withArrayWrapper(JsonSchema schema, boolean moveTitleAndDescription) {
    if (moveTitleAndDescription
        && (schema.getTitle().isPresent() || schema.getDescription().isPresent())) {
      JsonSchema itemSchema = schema;
      if (schema instanceof JsonSchemaString) {
        itemSchema =
            new ImmutableJsonSchemaString.Builder()
                .from(schema)
                .title(Optional.empty())
                .description(Optional.empty())
                .build();
      } else if (schema instanceof JsonSchemaInteger) {
        itemSchema =
            new ImmutableJsonSchemaInteger.Builder()
                .from(schema)
                .title(Optional.empty())
                .description(Optional.empty())
                .build();
      } else if (schema instanceof JsonSchemaBoolean) {
        itemSchema =
            new ImmutableJsonSchemaBoolean.Builder()
                .from(schema)
                .title(Optional.empty())
                .description(Optional.empty())
                .build();
      } else if (schema instanceof JsonSchemaNumber) {
        itemSchema =
            new ImmutableJsonSchemaNumber.Builder()
                .from(schema)
                .title(Optional.empty())
                .description(Optional.empty())
                .build();
      } else if (schema instanceof JsonSchemaObject) {
        itemSchema =
            new ImmutableJsonSchemaObject.Builder()
                .from(schema)
                .title(Optional.empty())
                .description(Optional.empty())
                .build();
      } else if (schema instanceof JsonSchemaRef) {
        itemSchema =
            new ImmutableJsonSchemaRef.Builder()
                .from(schema)
                .title(Optional.empty())
                .description(Optional.empty())
                .build();
      }
      return new ImmutableJsonSchemaArray.Builder()
          .name(schema.getName())
          .title(schema.getTitle())
          .description(schema.getDescription())
          .items(itemSchema)
          .build();
    }

    return new ImmutableJsonSchemaArray.Builder().name(schema.getName()).items(schema).build();
  }

  @Override
  protected JsonSchema withOneOfWrapper(
      Collection<JsonSchema> schema,
      Optional<String> name,
      Optional<String> label,
      Optional<String> description) {
    return new ImmutableJsonSchemaOneOf.Builder()
        .oneOf(schema)
        .name(name)
        .title(label)
        .description(description)
        .build();
  }
}
