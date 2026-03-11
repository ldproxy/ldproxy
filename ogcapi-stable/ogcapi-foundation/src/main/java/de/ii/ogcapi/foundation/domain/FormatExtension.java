/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

@AutoMultiBind
public interface FormatExtension extends ApiExtension {

  ApiMediaTypeContent HTML_CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new StringSchema().example("<html>...</html>"))
          .schemaRef("#/components/schemas/htmlSchema")
          .ogcApiMediaType(ApiMediaType.HTML_MEDIA_TYPE)
          .build();

  ApiMediaTypeContent SCHEMA_CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new ObjectSchema())
          // TODO with OpenAPI 3.1 change to a link to a property schema
          // "https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/schemas/v3.0/schema.json#/definitions/Schema"
          .schemaRef("#/components/schemas/anyObject")
          .ogcApiMediaType(ApiMediaType.JSON_SCHEMA_MEDIA_TYPE)
          .build();

  Schema<?> OBJECT_SCHEMA = new ObjectSchema();
  String OBJECT_SCHEMA_REF = "#/components/schemas/anyObject";

  Schema<?> BINARY_SCHEMA = new BinarySchema();
  String BINARY_SCHEMA_REF = "#/components/schemas/binary";

  /**
   * @return the media type of this format
   */
  ApiMediaType getMediaType();

  /**
   * @return the Schema and an optional example object for this format and a GET operation on this
   *     resource in this API
   */
  ApiMediaTypeContent getContent();

  /**
   * @return {@code true}, if the format can be used in POST, PUT or PATCH requests
   */
  default boolean canSupportTransactions() {
    return false;
  }

  /**
   * @return {@code true}, if the format should be enabled by default
   */
  default boolean isEnabledByDefault() {
    return true;
  }

  /**
   * @return {@code true}, if the format is only used internally and should not be advertised in the
   *     API documentation or the response
   */
  default boolean isInternal() {
    return false;
  }
}
