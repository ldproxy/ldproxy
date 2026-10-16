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
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import de.ii.xtraplatform.features.domain.SchemaDeriver;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public abstract class SchemaDeriverOpenApi extends SchemaDeriver<Schema<?>> {

  protected final String label;
  protected final Optional<String> description;

  public SchemaDeriverOpenApi(
      String label, Optional<String> description, Map<String, Codelist> codelists) {
    super(codelists);
    this.label = label;
    this.description = description;
  }

  @Override
  protected Optional<String> getPropertyName(Schema<?> property) {
    return Optional.ofNullable(property.getName());
  }

  @Override
  protected boolean isPropertyRequired(Schema<?> property) {
    return Objects.equals(property.getNullable(), false);
  }

  @Override
  protected Map<String, Schema<?>> getNestedProperties(Schema<?> property) {
    Map<String, Schema<?>> nestedProperties = new LinkedHashMap<>();

    if (Objects.nonNull(property.getProperties())) {
      property.getProperties().forEach(nestedProperties::put);
    }

    return nestedProperties;
  }

  @Override
  protected abstract Schema<?> buildRootSchema(
      FeatureSchema schema,
      Map<String, Schema<?>> properties,
      Map<String, Schema<?>> definitions,
      List<String> requiredProperties);

  @Override
  protected Schema<?> mergeRootSchemas(List<Schema<?>> rootSchemas) {
    Schema<?> rootSchema = rootSchemas.get(0);

    List<Schema> schemas = new ArrayList<>();

    rootSchemas.stream()
        .filter(Objects::nonNull)
        .filter(schema -> schema instanceof ObjectSchema)
        .map(schema -> (ObjectSchema) schema)
        .forEach(
            schema -> {
              if (Objects.nonNull(schema.getProperties())
                  && schema.getProperties().containsKey("properties")) {
                schemas.add(schema.getProperties().get("properties"));
              }
            });

    rootSchema.getProperties().put("properties", new ObjectSchema().anyOf(schemas));

    return rootSchema;
  }

  @Override
  protected Schema<?> buildObjectSchema(
      FeatureSchema schema, Map<String, Schema<?>> properties, List<String> requiredProperties) {
    if (schema.getObjectType().filter(ot -> Objects.equals(ot, "Link")).isPresent()) {
      return new Schema<>().name(schema.getName()).$ref("#/components/schemas/Link");
    }

    Schema<?> objectSchema = new ObjectSchema().name(schema.getName());

    if (schema.getLabel().isPresent()) {
      objectSchema.title(schema.getLabel().get());
    }
    if (schema.getDescription().isPresent()) {
      objectSchema.description(schema.getDescription().get());
    }

    objectSchema.properties(new LinkedHashMap<>());
    objectSchema.getProperties().putAll(properties);
    if (!requiredProperties.isEmpty()) {
      objectSchema.required(requiredProperties);
    }

    return objectSchema;
  }

  @Override
  protected Schema<?> getSchemaForLiteralType(
      Type type,
      Optional<String> label,
      Optional<String> description,
      Optional<String> unit,
      Optional<String> role,
      Optional<String> refCollectionId,
      Optional<String> refApiLandingPage,
      Optional<String> codelistUri) {
    Schema<?> valueSchema;
    switch (type) {
      case INTEGER:
        valueSchema = new IntegerSchema();
        break;
      case FLOAT:
        valueSchema = new NumberSchema();
        break;
      case BOOLEAN:
        valueSchema = new BooleanSchema();
        break;
      case DATETIME:
        valueSchema = new DateTimeSchema();
        break;
      case DATE:
        valueSchema = new DateSchema();
        break;
      case STRING:
      default:
        valueSchema = new StringSchema();
        break;
    }

    label.ifPresent(valueSchema::title);
    description.ifPresent(valueSchema::description);

    return valueSchema;
  }

  @Override
  protected Schema<?> getSchemaForGeometry(
      List<GeometryType> geometryTypes,
      Optional<String> title,
      Optional<String> description,
      Optional<String> role) {
    Schema<?> oapiSchema =
        switch (GeometryType.effectiveType(geometryTypes)) {
          case POINT ->
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/pointGeoJSON");
          case MULTI_POINT ->
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/multipointGeoJSON");
          case LINE_STRING ->
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/linestringGeoJSON");
          case MULTI_LINE_STRING ->
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/multilinestringGeoJSON");
          case POLYGON ->
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/polygonGeoJSON");
          case MULTI_POLYGON ->
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/multipolygonGeoJSON");
          case ANY ->
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/geometryGeoJSON");
          default -> new ObjectSchema();
        };
    // NOTE OpenAPI 3.0 does not expect other members next to '$ref',
    // so title and description are not added
    return oapiSchema;
  }

  @Override
  protected Schema<?> withPropertySeq(Schema<?> schema, int propertySeq) {
    return schema; // x-ogc-propertySeq will for now not be included in OpenAPI schemas
  }

  @Override
  protected Schema<?> withName(Schema<?> oapiSchema, String propertyName) {
    oapiSchema.setName(propertyName);
    return oapiSchema;
  }

  @Override
  protected Schema<?> withRequired(Schema<?> oapiSchema) {
    oapiSchema.nullable(false);
    return oapiSchema;
  }

  @Override
  protected Schema<?> withReadOnly(Schema<?> oapiSchema) {
    oapiSchema.readOnly(true);
    return oapiSchema;
  }

  @Override
  protected Schema<?> withWriteOnly(Schema<?> oapiSchema) {
    oapiSchema.writeOnly(true);
    return oapiSchema;
  }

  @Override
  protected Schema<?> withConstraints(
      Schema<?> oapiSchema,
      SchemaConstraints constraints,
      FeatureSchema property,
      Map<String, Codelist> codelists) {
    if (oapiSchema instanceof ArraySchema) {
      if (constraints.getMinOccurrence().isPresent()) {
        oapiSchema.minItems(constraints.getMinOccurrence().get());
      }
      if (constraints.getMaxOccurrence().isPresent()) {
        oapiSchema.maxItems(constraints.getMaxOccurrence().get());
      }
    }

    Schema<?> result = oapiSchema;
    if (constraints.getRequired().isPresent() && constraints.getRequired().get()) {
      result = withRequired(result);
    }

    // the constraints of an array apply to its values, not to the array
    Schema<?> valueSchema =
        result instanceof ArraySchema && Objects.nonNull(result.getItems())
            ? result.getItems()
            : result;

    boolean isString =
        property.isArray()
            ? property.getValueType().orElse(Type.UNKNOWN) != Type.INTEGER
            : property.getType() != Type.INTEGER;
    Optional<Codelist> codelist =
        constraints.getCodelist().map(codelists::get).filter(Objects::nonNull);
    // if enum is specified in the configuration, it wins over codelist
    List<String> codes =
        !constraints.getEnumValues().isEmpty()
            ? constraints.getEnumValues()
            : codelist.isPresent() && codelist.get().getFallback().isEmpty()
                ? List.copyOf(codelist.get().getEntries().keySet())
                : List.of();
    if (!codes.isEmpty()) {
      List<Object> values = new ArrayList<>();
      codes.forEach(code -> values.add(isString ? code : Integer.parseInt(code)));
      List<String> titles =
          withCodelistTitles() && codelist.isPresent()
              ? codes.stream()
                  .map(code -> codelist.get().getEntries().get(code))
                  .filter(Objects::nonNull)
                  .filter(title -> !codes.contains(title))
                  .distinct()
                  .toList()
              : List.of();
      if (!titles.isEmpty()) {
        values.addAll(titles);
        if (!isString) {
          // a title is a string, so the value is either an integer code or a string title
          valueSchema.setType(null);
          valueSchema.setFormat(null);
        }
      }
      ((Schema<Object>) valueSchema).setEnum(values);
    }
    if (constraints.getRegex().isPresent()) {
      valueSchema.setPattern(constraints.getRegex().get());
    }
    if (constraints.getMin().isPresent()) {
      valueSchema.setMinimum(BigDecimal.valueOf(constraints.getMin().get()));
    }
    if (constraints.getMax().isPresent()) {
      valueSchema.setMaximum(BigDecimal.valueOf(constraints.getMax().get()));
    }

    return oapiSchema;
  }

  /**
   * @return {@code true}, if the value of a property with a codelist may also be the title of the
   *     code, e.g. in a response with a profile that represents codes by their title
   */
  protected boolean withCodelistTitles() {
    return false;
  }

  @Override
  protected Schema<?> withRefWrapper(Schema<?> schema, String objectType) {
    return schema;
  }

  @Override
  protected Schema<?> withArrayWrapper(Schema<?> oapiSchema, boolean moveTitleAndDescription) {
    if (moveTitleAndDescription
        && (Objects.nonNull(oapiSchema.getTitle())
            || Objects.nonNull(oapiSchema.getDescription()))) {
      String title = oapiSchema.getTitle();
      String desc = oapiSchema.getDescription();
      return new ArraySchema()
          .items(oapiSchema.title(null).description(null))
          .name(oapiSchema.getName())
          .title(title)
          .description(desc);
    }
    oapiSchema.getDescription();
    return new ArraySchema().items(oapiSchema).name(oapiSchema.getName());
  }

  @Override
  protected Schema<?> withOneOfWrapper(
      Collection<Schema<?>> schema,
      Optional<String> name,
      Optional<String> label,
      Optional<String> description) {
    Schema<?> schema1 = new Schema<>();
    schema1.oneOf(new ArrayList<>(schema));
    name.ifPresent(schema1::name);
    label.ifPresent(schema1::title);
    description.ifPresent(schema1::description);

    return schema1;
  }
}
