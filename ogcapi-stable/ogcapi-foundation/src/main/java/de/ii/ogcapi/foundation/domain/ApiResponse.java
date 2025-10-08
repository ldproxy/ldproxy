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
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import org.immutables.value.Value;

/**
 * # Response encoding
 *
 * @langEn For operations that return a response, the encoding is chosen using standard HTTP content
 *     negotiation with `Accept` headers.
 *     <p>GET operations additionally support the query parameter `f`, which allows to explicitely
 *     choose the encoding and override the result of the content negotiation. The supported
 *     encodings depend on the affected resource and the configuration.
 * @langDe Bei Operationen, die eine Antwort zurückliefern, wird das Format nach den
 *     Standard-HTTP-Regeln standardmäßig über Content-Negotiation und den `Accept`-Header
 *     ermittelt.
 *     <p>Alle GET-Operationen unterstützen zusätzlich den Query-Parameter `f`. Über diesen
 *     Parameter kann das Ausgabeformat der Antwort auch direkt ausgewählt werden. Wenn kein Wert
 *     angegeben wird, gelten die Standard-HTTP-Regeln, d.h. der `Accept`-Header wird zur Bestimmung
 *     des Formats verwendet. Die unterstützten Formate hängen von der Ressource und von der
 *     API-Konfiguration ab.
 */

/**
 * # Response language
 *
 * @langEn For operations that return a response, the language for linguistic texts is chosen using
 *     standard HTTP content negiotiation with `Accept-Language` headers.
 *     <p>If enabled in [Common Core](common.md), GET operations additionally support the quer
 *     parameter `lang`, which allows to explicitely choose the language and override the result of
 *     the content negotiation. The supported languages depend on the affected resource and the
 *     configuration. Support for multilingualism is currently limited. There are four possible
 *     sources for linguistic texts:
 *     <p><code>
 * - Static texts: For example link labels or static texts in HTML representations. Currently, the languages English (`en`) and German (`de`) are supported.
 * - Texts contained in the data: Currently not supported.
 * - Texts set in the configuration: Currently not supported.
 * - Error messages: These are always in english, the messages are currently hard-coded.
 *     </code>
 * @langDe Bei Operationen, die eine Antwort zurückliefern, wird die verwendete Sprache bei
 *     linguistischen Texten nach den Standard-HTTP-Regeln standardmäßig über Content-Negotiation
 *     und den `Accept-Language`-Header ermittelt.
 *     <p>Sofern die entsprechende Option im Baustein "Common Core" aktiviert ist, unterstützen alle
 *     GET-Operationen zusätzlich den Query-Parameter `lang`. Über diesen Parameter kann die Sprache
 *     auch direkt ausgewählt werden. Wenn kein Wert angegeben wird, gelten die
 *     Standard-HTTP-Regeln, wie oben beschrieben. Die erlaubten Werte hängen von der Ressource und
 *     von der API-Konfiguration ab. Die Unterstützung für Mehrsprachigkeit ist derzeit begrenzt. Es
 *     gibt vier Arten von Quellen für Texte:
 *     <p><code>
 * - Texte zu festen Elementen der API: Diese werden von ldproxy erzeugt, z.B. die Texte der Titel von Links oder feste Textbausteine in der HTML-Ausgabe. Derzeit werden die Sprachen "Deutsch" (de) und "Englisch" (en) unterstützt.
 * - Texte aus Attributen in den Daten: Hier gibt es noch keine Unterstützung, wie die Rückgabe bei mehrsprachigen Daten in Abhängigkeit von der Ausgabesprache gesteuert werden kann.
 * - Texte aus der API-Konfiguration, insbesondere zum Datenschema: Hier gibt es noch keine Unterstützung, wie die Rückgabe bei mehrsprachigen Daten in Abhängigkeit von der Ausgabesprache gesteuert werden kann.
 * - Fehlermeldungen der API: Diese sind immer in Englisch, die Meldungen sind aktuell Bestandteil des Codes.
 *     </code>
 */
@Value.Immutable
public interface ApiResponse {
  @Value.Default
  default String getStatusCode() {
    return "200";
  }

  Optional<String> getId();

  String getDescription();

  List<ApiHeader> getHeaders();

  Map<MediaType, ApiMediaTypeContent> getContent();

  default void updateOpenApiDefinition(OgcApiDataV2 apiData, OpenAPI openAPI, Operation op) {
    io.swagger.v3.oas.models.responses.ApiResponse response =
        new io.swagger.v3.oas.models.responses.ApiResponse();
    response.description(getDescription());
    getHeaders().forEach(header -> header.updateOpenApiDefinition(apiData, openAPI, response));
    Content content = new Content();
    getContent()
        .forEach(
            (key, value) -> content.addMediaType(key.toString(), newMediaType(openAPI, value)));
    response.content(content);

    getId()
        .ifPresentOrElse(
            id -> {
              if (!openAPI.getComponents().getResponses().containsKey(id)) {
                openAPI.getComponents().getResponses().put(id, response);
              }
              op.responses(
                  new ApiResponses()
                      .addApiResponse(
                          getStatusCode(),
                          new io.swagger.v3.oas.models.responses.ApiResponse()
                              .$ref("#/components/responses/" + id)));
            },
            () -> {
              op.responses(new ApiResponses().addApiResponse(getStatusCode(), response));
            });
  }

  private io.swagger.v3.oas.models.media.MediaType newMediaType(
      OpenAPI openAPI, ApiMediaTypeContent content) {
    io.swagger.v3.oas.models.media.MediaType mediaType =
        new io.swagger.v3.oas.models.media.MediaType();
    String schemaRef = content.getSchemaRef();
    mediaType.schema(new Schema<>().$ref(schemaRef));
    if (schemaRef.startsWith("#/components/schemas/")) {
      String schemaId = schemaRef.substring(21);
      if (!openAPI.getComponents().getSchemas().containsKey(schemaId)) {
        openAPI.getComponents().getSchemas().put(schemaId, content.getSchema());
      }
      content
          .referencedSchemas()
          .forEach(
              (refSchemaId, refSchema) -> {
                if (!openAPI.getComponents().getSchemas().containsKey(refSchemaId)) {
                  openAPI.getComponents().getSchemas().put(refSchemaId, refSchema);
                }
              });
    }

    // Just set the first "example" as Swagger UI does not show "examples"
    content.getExamples().stream()
        .map(Example::getValue)
        .flatMap(Optional::stream)
        .findFirst()
        .ifPresent(mediaType::setExample);

    return mediaType;
  }
}
