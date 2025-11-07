/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * @langEn General metadata for the API (version, contact details, license information). Supported
 *     keys with affected resources:
 *     <p><code>
 * - `version`: API Definition,
 * - `contactName`: API Definition, HTML Landing Page
 * - `contactUrl`: API Definition, HTML Landing Page
 * - `contactEmail`: API Definition, HTML Landing Page
 * - `contactPhone`: HTML Landing Page,
 * - `license`: API Definition, Landing Page, Feature Collections, Feature Collection
 * - `licenseName`: API Definition, Landing Page, Feature Collections, Feature Collection
 * - `licenseUrl`: API Definition, Landing Page, Feature Collections, Feature Collection
 * - `keywords`: HTML meta tages and schema:Dataset in HTML Landing Page
 * - `attribution`: Landing Page, maps
 * - `creatorName`: schema:Dataset in HTML
 * - `creatorUrl`: schema:Dataset in HTML
 * - `creatorLogoUrl`: schema:Dataset in HTML
 * - `publisherName`: schema:Dataset in HTML
 * - `publisherUrl`: schema:Dataset in HTML
 * - `publisherLogoUrl`: schema:Dataset in HTML
 *     </code>
 *     <p>All values are strings, except `keywords`, which is an array of strings.
 * @langDe Über dieses Objekt können grundlegende Metadaten zur API (Version, Kontaktdaten,
 *     Lizenzinformationen) festgelegt werden. Erlaubt sind die folgenden Elemente mit den
 *     Ressourcen, in denen die Angabe verwendet wird:
 *     <p><code>
 * - `version`: API-Definition
 * - `contactName`: API-Definition, HTML-Landing-Page
 * - `contactUrl`: API-Definition, HTML-Landing-Page
 * - `contactEmail`: API-Definition, HTML-Landing-Page
 * - `contactPhone`: HTML-Landing-Page
 * - `license`: API-Definition, Landing-Page, Feature-Collections, Feature-Collection
 * - `licenseName`: API-Definition, HTML-Landing-Page, Feature-Collections, Feature-Collection
 * - `licenseUrl`: API-Definition, HTML-Landing-Page, Feature-Collections, Feature-Collection
 * - `keywords`: Meta-Tags und schema:Dataset in HTML-Landing-Page
 * - `attribution`: Landing-Page, Karten
 * - `creatorName`: schema:Dataset in HTML
 * - `creatorUrl`: schema:Dataset in HTML
 * - `creatorLogoUrl`: schema:Dataset in HTML
 * - `publisherName`: schema:Dataset in HTML
 * - `publisherUrl`: schema:Dataset in HTML
 * - `publisherLogoUrl`: schema:Dataset in HTML
 *     </code>
 *     <p>Alle Angaben sind Strings, bis auf `keywords`, die als Array von Strings angegeben werden.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableApiMetadata.Builder.class)
public interface ApiMetadata {

  /**
   * @langEn Optional name of a contact person or organization for the API.
   * @langDe Optionaler Name einer Kontaktperson oder -organisation für die API.
   */
  Optional<String> getContactName();

  /**
   * @langEn Optional URL of a contact webpage for the API.
   * @langDe Optionale URL einer Kontakt-Webseite für die API.
   */
  Optional<String> getContactUrl();

  /**
   * @langEn Optional email address for information about the API.
   * @langDe Optionale Emailadresse für Informationen über die API.
   */
  Optional<String> getContactEmail();

  /**
   * @langEn Optional phone number for information about the API.
   * @langDe Optionale Telefonnummer für Informationen über die API.
   */
  Optional<String> getContactPhone();

  /**
   * @langEn Optional name of a creator of data shared via the API.
   * @langDe Optionaler Name des Erzeugers der Daten aus dieser API.
   * @since v3.0
   */
  Optional<String> getCreatorName();

  /**
   * @langEn Optional URL of a website of the creator of data shared via the API.
   * @langDe Optionale URL der Website des Erzeugers der Daten aus dieser API.
   * @since v3.0
   */
  Optional<String> getCreatorUrl();

  /**
   * @langEn Optional URL of a logo bitmap image of the creator of data shared via the API.
   * @langDe Optionale URL einer Logo-Bilddatei des Erzeugers der Daten aus dieser API.
   * @since v3.0
   */
  Optional<String> getCreatorLogoUrl();

  /**
   * @langEn Optional name of the publisher of this API.
   * @langDe Optionaler Name des Herausgebers dieser API.
   * @since v3.0
   */
  Optional<String> getPublisherName();

  /**
   * @langEn Optional URL of a website of the publisher of this API.
   * @langDe Optionale URL der Website des Herausgebers dieser API.
   * @since v3.0
   */
  Optional<String> getPublisherUrl();

  /**
   * @langEn Optional URL of a logo bitmap image of the publisher of this API.
   * @langDe Optionale URL einer Logo-Bilddatei des Herausgebers dieser API.
   * @since v3.0
   */
  Optional<String> getPublisherLogoUrl();

  /**
   * @langEn SPDX license identifier of the license of the data shared via this API.
   * @langDe SPDX-Lizenzidentifikator der Lizenz der Daten aus dieser API.
   * @since v4.6
   */
  Optional<String> getLicense();

  /**
   * @langEn Name of the license of the data shared via this API. If not set and `license` is set,
   *     the name is derived from the SPDX identifier.
   * @langDe Name der Lizenz der Daten aus dieser API. Falls nicht gesetzt und `license` ist
   *     gesetzt, dann wird der Name aus dem SPDX-Identifikator abgeleitet.
   */
  Optional<String> getLicenseName();

  /**
   * @langEn URL of the license of the data shared via this API. If not set and `license` is set,
   *     the URL is derived from the SPDX identifier.
   * @langDe URL der Lizenz der Daten aus dieser API. Falls nicht gesetzt und `license` ist gesetzt,
   *     dann wird die URL aus dem SPDX-Identifikator abgeleitet.
   */
  Optional<String> getLicenseUrl();

  /**
   * @langEn Keywords describing this API.
   * @langDe Schlagworte die diese API beschreiben.
   * @default []
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getKeywords();

  /**
   * @langEn Version for this API in the OpenAPI definition.
   * @langDe Version der API in der OpenAPI-Definition.
   * @default `1.0.0`
   */
  Optional<String> getVersion();

  /**
   * @langEn Attribution text for data shared via this API, e.g., for display in maps.
   * @langDe Text für die Namensnennung, wenn Daten aus dieser API verwendet werden, z.B. in einer
   *     Karte.
   * @since v3.0
   */
  Optional<String> getAttribution();
}
