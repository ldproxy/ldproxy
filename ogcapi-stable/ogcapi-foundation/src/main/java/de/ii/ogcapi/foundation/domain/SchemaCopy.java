/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Copies of OpenAPI schemas. The schema of a parameter is shared by every use of the parameter, so
 * a use that changes a member, for example the description, works on a copy.
 */
public final class SchemaCopy {

  private SchemaCopy() {}

  /**
   * A copy of the schema whose own members can be changed without changing the original; the
   * subschemas (items, properties, alternatives) are shared with the original. A full copy through
   * the deserializer would turn a subschema without a type, for example an alternative that only
   * restricts the number of items of an array, into an object schema.
   */
  public static Schema<?> shallow(Schema<?> schema) {
    Schema<?> copy = Json.mapper().convertValue(schema, Schema.class);
    copy.setItems(schema.getItems());
    copy.setProperties(schema.getProperties());
    copy.setAdditionalProperties(schema.getAdditionalProperties());
    copy.setNot(schema.getNot());
    copy.setAllOf(schema.getAllOf());
    copy.setAnyOf(schema.getAnyOf());
    copy.setOneOf(schema.getOneOf());
    return copy;
  }
}
