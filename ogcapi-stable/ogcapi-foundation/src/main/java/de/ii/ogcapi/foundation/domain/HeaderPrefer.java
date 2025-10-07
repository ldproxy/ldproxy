/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.google.common.collect.ImmutableList;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

public abstract class HeaderPrefer extends ApiExtensionCache implements ApiHeader {

  private final Schema<?> schema =
      new StringSchema()
          ._enum(ImmutableList.of("handling=strict", "handling=lenient"))
          ._default("handling=lenient");
  protected final SchemaValidator schemaValidator;

  protected HeaderPrefer(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getName() {
    return "Prefer";
  }

  @Override
  public boolean isRequestHeader() {
    return true;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }
}
