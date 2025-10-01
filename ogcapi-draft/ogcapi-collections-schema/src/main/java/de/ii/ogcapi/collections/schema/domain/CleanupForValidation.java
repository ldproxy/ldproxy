/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.domain;

import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaAllOf;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaArray;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaBoolean;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaConstant;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaDocument;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaFalse;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaGeometry;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaInteger;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaNull;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaNumber;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaObject;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaOneOf;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaRef;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaString;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaTrue;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaAllOf;
import de.ii.ogcapi.features.core.domain.JsonSchemaArray;
import de.ii.ogcapi.features.core.domain.JsonSchemaBoolean;
import de.ii.ogcapi.features.core.domain.JsonSchemaConstant;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument;
import de.ii.ogcapi.features.core.domain.JsonSchemaFalse;
import de.ii.ogcapi.features.core.domain.JsonSchemaGeometry;
import de.ii.ogcapi.features.core.domain.JsonSchemaInteger;
import de.ii.ogcapi.features.core.domain.JsonSchemaNull;
import de.ii.ogcapi.features.core.domain.JsonSchemaNumber;
import de.ii.ogcapi.features.core.domain.JsonSchemaObject;
import de.ii.ogcapi.features.core.domain.JsonSchemaOneOf;
import de.ii.ogcapi.features.core.domain.JsonSchemaRef;
import de.ii.ogcapi.features.core.domain.JsonSchemaString;
import de.ii.ogcapi.features.core.domain.JsonSchemaTrue;
import de.ii.ogcapi.features.core.domain.JsonSchemaVisitor;
import java.util.Optional;

public class CleanupForValidation implements JsonSchemaVisitor {

  // TODO find a cleaner way to do this

  @Override
  public JsonSchema visit(JsonSchema schema) {
    JsonSchema newSchema = visitProperties(schema);
    if (newSchema instanceof JsonSchemaString string) {
      return new ImmutableJsonSchemaString.Builder()
          .from(string)
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
    } else if (newSchema instanceof JsonSchemaInteger integer) {
      return new ImmutableJsonSchemaInteger.Builder()
          .from(integer)
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
    } else if (newSchema instanceof JsonSchemaNumber number) {
      return new ImmutableJsonSchemaNumber.Builder()
          .from(number)
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
    } else if (newSchema instanceof JsonSchemaBoolean bool) {
      return new ImmutableJsonSchemaBoolean.Builder()
          .from(bool)
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
    } else if (newSchema instanceof JsonSchemaAllOf allOf) {
      return new ImmutableJsonSchemaAllOf.Builder()
          .from(allOf)
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
    } else if (newSchema instanceof JsonSchemaOneOf oneOf) {
      return new ImmutableJsonSchemaOneOf.Builder()
          .from(oneOf)
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
    } else if (newSchema instanceof JsonSchemaConstant constant) {
      return new ImmutableJsonSchemaConstant.Builder()
          .from(constant)
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
    } else if (newSchema instanceof JsonSchemaArray array) {
      return new ImmutableJsonSchemaArray.Builder()
          .from(array)
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
    } else if (newSchema instanceof JsonSchemaDocument document) {
      return ImmutableJsonSchemaDocument.builder()
          .from(document)
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
    } else if (newSchema instanceof JsonSchemaObject object) {
      return new ImmutableJsonSchemaObject.Builder()
          .from(object)
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
    } else if (newSchema instanceof JsonSchemaNull nil) {
      return new ImmutableJsonSchemaNull.Builder()
          .from(nil)
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
    } else if (newSchema instanceof JsonSchemaGeometry geom) {
      return new ImmutableJsonSchemaGeometry.Builder()
          .from(geom)
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
    } else if (newSchema instanceof JsonSchemaRef ref) {
      return new ImmutableJsonSchemaRef.Builder()
          .from(ref)
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
    } else if (newSchema instanceof JsonSchemaTrue tr) {
      return ImmutableJsonSchemaTrue.builder()
          .from(tr)
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
    } else if (newSchema instanceof JsonSchemaFalse fa) {
      return ImmutableJsonSchemaFalse.builder()
          .from(fa)
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

    throw new IllegalStateException(
        "Unexpected JSON Schema type: " + newSchema.getClass().getSimpleName());
  }
}
