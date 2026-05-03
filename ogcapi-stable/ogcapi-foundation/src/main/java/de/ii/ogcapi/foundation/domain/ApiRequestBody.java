/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Map;
import java.util.Optional;
import jakarta.ws.rs.core.MediaType;
import org.immutables.value.Value;

@Value.Immutable
public interface ApiRequestBody {
  default String getStatusCode() {
    return "200";
  }

  Optional<String> getId();

  String getDescription();

  Map<MediaType, ApiMediaTypeContent> getContent();

  default boolean isRequired() {
    return false;
  }

  default void updateOpenApiDefinition(OpenAPI openAPI, Operation op) {
    RequestBody body = new RequestBody();
    body.description(getDescription());
    body.required(isRequired());
    Content content = new Content();
    getContent()
        .forEach(
            (key, value) -> {
              io.swagger.v3.oas.models.media.MediaType mediaType =
                  new io.swagger.v3.oas.models.media.MediaType();
              String schemaRef = value.getSchemaRef();
              mediaType.schema(new Schema<>().$ref(schemaRef));
              if (schemaRef.startsWith("#/components/schemas/")) {
                String schemaId = schemaRef.substring(21);
                if (!openAPI.getComponents().getSchemas().containsKey(schemaId)) {
                  openAPI.getComponents().getSchemas().put(schemaId, value.getSchema());
                }
              }
              value
                  .referencedSchemas()
                  .forEach(
                      (refSchemaId, refSchema) -> {
                        if (!openAPI.getComponents().getSchemas().containsKey(refSchemaId)) {
                          openAPI.getComponents().getSchemas().put(refSchemaId, refSchema);
                        }
                      });

              // Just set the first "example" as Swagger UI does not show "examples"
              value.getExamples().stream()
                  .map(Example::getValue)
                  .flatMap(Optional::stream)
                  .findFirst()
                  .ifPresent(mediaType::setExample);

              content.addMediaType(key.toString(), mediaType);
            });
    body.content(content);

    getId()
        .ifPresentOrElse(
            id -> {
              if (!openAPI.getComponents().getRequestBodies().containsKey(id)) {
                openAPI.getComponents().getRequestBodies().put(id, body);
              }
              op.requestBody(new RequestBody().$ref("#/components/requestBodies/" + id));
            },
            () -> {
              op.requestBody(body);
            });
  }
}
