/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaArray;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaBoolean;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaNumber;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaRef;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaVisitor;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;

public class OpenApiSchemaDeriver implements JsonSchemaVisitor<Schema<?>> {

  public Schema<?> visit(JsonSchema jsonSchema) {
    Schema<?> schema;
    if (jsonSchema instanceof JsonSchemaRef ref) {
      schema = new ObjectSchema();
      schema.$ref(ref.getRef());
    } else if (jsonSchema instanceof JsonSchemaObject typedSchema) {
      schema = getObjectSchema(typedSchema);
    } else if (jsonSchema instanceof JsonSchemaArray typedSchema) {
      schema = getArraySchema(typedSchema);
    } else if (jsonSchema instanceof JsonSchemaString typedSchema) {
      schema = getStringSchema(typedSchema);
    } else if (jsonSchema instanceof JsonSchemaNumber typedSchema) {
      schema = getNumberSchema(typedSchema);
    } else if (jsonSchema instanceof JsonSchemaInteger typedSchema) {
      schema = getIntegerSchema(typedSchema);
    } else if (jsonSchema instanceof JsonSchemaBoolean) {
      schema = new BooleanSchema();
    } else {
      throw new IllegalArgumentException(
          String.format("Found unsupported JSON Schema type. Found: %s", jsonSchema));
    }
    setMetadata(jsonSchema, schema);
    return schema;
  }

  private Schema<?> getObjectSchema(JsonSchemaObject object) {
    ObjectSchema schema = new ObjectSchema();
    object.getRequired().forEach(schema::addRequiredItem);
    object
        .getProperties()
        .forEach(
            (pName, pSchema) -> {
              schema.addProperty(pName, pSchema.accept(this));
            });
    // see limitation, not supported: patternProperties, additionalProperties, allOf, oneOf
    return schema;
  }

  private Schema<?> getArraySchema(JsonSchemaArray array) {
    ArraySchema schema = new ArraySchema();
    array.getItems().ifPresent(v -> schema.items(v.accept(this)));
    array.getMinItems().ifPresent(schema::minItems);
    array.getMaxItems().ifPresent(schema::maxItems);
    // see limitation, not supported: uniqueObject, prefixItems, additionalItems, items:false
    return schema;
  }

  private Schema<?> getStringSchema(JsonSchemaString string) {
    StringSchema schema = new StringSchema();
    string.getFormat().ifPresent(schema::format);
    string.getMinLength().ifPresent(schema::minLength);
    string.getMaxLength().ifPresent(schema::maxLength);
    string.getPattern().ifPresent(schema::pattern);
    string.getEnums().ifPresent(values -> values.forEach(schema::addEnumItem));
    return schema;
  }

  private Schema<?> getNumberSchema(JsonSchemaNumber number) {
    NumberSchema schema = new NumberSchema();
    number.getMinimum().ifPresent(v -> schema.minimum(BigDecimal.valueOf(v)));
    number.getMaximum().ifPresent(v -> schema.maximum(BigDecimal.valueOf(v)));
    return schema;
  }

  private Schema<?> getIntegerSchema(JsonSchemaInteger integer) {
    IntegerSchema schema = new IntegerSchema();
    integer.getMinimum().ifPresent(v -> schema.minimum(BigDecimal.valueOf(v)));
    integer.getMaximum().ifPresent(v -> schema.maximum(BigDecimal.valueOf(v)));
    integer.getEnums().ifPresent(values -> values.forEach(schema::addEnumItem));
    return schema;
  }

  private void setMetadata(JsonSchema jsonSchema, Schema<?> schema) {
    jsonSchema.getTitle().ifPresent(schema::setTitle);
    jsonSchema.getDescription().ifPresent(schema::setDescription);
    jsonSchema.getDefault_().ifPresent(schema::setDefault);
  }
}
