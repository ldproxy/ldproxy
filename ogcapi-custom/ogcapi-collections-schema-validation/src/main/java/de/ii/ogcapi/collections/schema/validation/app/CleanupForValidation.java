/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.validation.app;

import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaAllOf;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaArray;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaBoolean;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaConstant;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaFalse;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaGeometry;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaNull;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaNumber;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaOneOf;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaRef;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaString;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaTrue;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaAllOf;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaArray;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaBoolean;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaConstant;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaFalse;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaGeometry;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaNull;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaNumber;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaOneOf;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaRef;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaTrue;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaVisitor;
import java.util.Optional;

public class CleanupForValidation implements JsonSchemaVisitor {

  @Override
  public JsonSchema visit(JsonSchema schema) {
    JsonSchema newSchema = visitProperties(schema);

    JsonSchema.Builder builder;

    if (newSchema instanceof JsonSchemaString string) {
      builder = new ImmutableJsonSchemaString.Builder().from(string);
    } else if (newSchema instanceof JsonSchemaInteger integer) {
      builder = new ImmutableJsonSchemaInteger.Builder().from(integer);
    } else if (newSchema instanceof JsonSchemaNumber number) {
      builder = new ImmutableJsonSchemaNumber.Builder().from(number);
    } else if (newSchema instanceof JsonSchemaBoolean bool) {
      builder = new ImmutableJsonSchemaBoolean.Builder().from(bool);
    } else if (newSchema instanceof JsonSchemaAllOf allOf) {
      builder = new ImmutableJsonSchemaAllOf.Builder().from(allOf);
    } else if (newSchema instanceof JsonSchemaOneOf oneOf) {
      builder = new ImmutableJsonSchemaOneOf.Builder().from(oneOf);
    } else if (newSchema instanceof JsonSchemaConstant constant) {
      builder = new ImmutableJsonSchemaConstant.Builder().from(constant);
    } else if (newSchema instanceof JsonSchemaArray array) {
      builder = new ImmutableJsonSchemaArray.Builder().from(array);
    } else if (newSchema instanceof JsonSchemaDocument document) {
      builder = ImmutableJsonSchemaDocument.builder().from(document);
    } else if (newSchema instanceof JsonSchemaObject object) {
      builder = new ImmutableJsonSchemaObject.Builder().from(object);
    } else if (newSchema instanceof JsonSchemaNull nil) {
      builder = new ImmutableJsonSchemaNull.Builder().from(nil);
    } else if (newSchema instanceof JsonSchemaGeometry geom) {
      builder = new ImmutableJsonSchemaGeometry.Builder().from(geom);
    } else if (newSchema instanceof JsonSchemaRef ref) {
      builder = new ImmutableJsonSchemaRef.Builder().from(ref);
    } else if (newSchema instanceof JsonSchemaTrue tr) {
      builder = ImmutableJsonSchemaTrue.builder().from(tr);
    } else if (newSchema instanceof JsonSchemaFalse fa) {
      builder = ImmutableJsonSchemaFalse.builder().from(fa);
    } else {
      throw new IllegalStateException(
          "Unexpected JSON Schema type: " + newSchema.getClass().getSimpleName());
    }

    return builder
        .title(Optional.empty())
        .description(Optional.empty())
        .readOnly(Optional.empty())
        .writeOnly(Optional.empty())
        .codelistUri(Optional.empty())
        .role(Optional.empty())
        .embeddedRole(Optional.empty())
        .propertySeq(Optional.empty())
        .refCollectionId(Optional.empty())
        .refUriTemplate(Optional.empty())
        .build();
  }
}
