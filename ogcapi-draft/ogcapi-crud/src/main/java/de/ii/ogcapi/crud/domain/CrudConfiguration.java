/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.Objects;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock CRUD
 * @buildingBlockAlias TRANSACTIONAL
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: CRUD
 *   enabled: true
 *   rejectEmptyValues: true
 *   returnRepresentation: true
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "CRUD", aliases = "TRANSACTIONS")
@JsonDeserialize(builder = ImmutableCrudConfiguration.Builder.class)
public interface CrudConfiguration extends ExtensionConfiguration {

  /**
   * @langEn Option to enable support for conditional processing of PUT, PATCH, and DELETE requests,
   *     based on the time when the feature was last updated. Such requests must include an
   *     `If-Unmodified-Since` header, otherwise they will be rejected with HTTP 428 ("Precondition
   *     Required"). A feature will only be changed, if the feature was not changed since the
   *     timestamp in the header (or if no last modification time is known for the feature),
   *     otherwise the response is HTTP 412 ("Precondition Failed"). The response to a successful
   *     PUT or PATCH request includes the new last modification time in a `Last-Modified` header,
   *     so that it can be used in the next conditional request. The last modification time of a
   *     feature is determined from a feature property with type `DATETIME` for which
   *     `isLastModified` is set to true in the schema in the feature provider; the property may be
   *     excluded from the scope `RECEIVABLE`.
   * @langDe Option zur Aktivierung der Unterstützung für die bedingte Verarbeitung von PUT-, PATCH-
   *     und DELETE-Anfragen, basierend auf der Zeit, zu der das Feature zuletzt aktualisiert wurde.
   *     Solche Anfragen müssen einen `If-Unmodified-Since`-Header enthalten, andernfalls werden sie
   *     mit HTTP 428 ("Precondition Required") zurückgewiesen. Ein Feature wird nur dann geändert,
   *     wenn das Feature seit dem Zeitstempel im Header nicht geändert wurde (oder wenn kein
   *     letzter Änderungszeitpunkt für das Feature bekannt ist), andernfalls ist die Antwort HTTP
   *     412 ("Precondition Failed"). Die Antwort auf eine erfolgreiche PUT- oder PATCH-Anfrage
   *     enthält den neuen Änderungszeitpunkt in einem `Last-Modified`-Header, damit er in der
   *     nächsten bedingten Anfrage verwendet werden kann. Der Zeitpunkt der letzten Änderung eines
   *     Features wird anhand einer Objekteigenschaft mit Datentyp `DATETIME` ermittelt, für die
   *     `isLastModified` im Schema des Feature Providers auf `true` gesetzt ist; die Eigenschaft
   *     kann vom Geltungsbereich `RECEIVABLE` ausgenommen werden.
   * @default false
   * @since v3.5
   */
  @Nullable
  Boolean getOptimisticLockingLastModified();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean supportsLastModified() {
    return Objects.equals(getOptimisticLockingLastModified(), true);
  }

  /**
   * @langEn Option to reject empty values in the request body of a POST or PUT request. A value is
   *     empty, if it is a string without characters or with only whitespace. The check is only
   *     applied to requests with a `Prefer` header with the value "handling=strict"; such a request
   *     is rejected with HTTP 400 ("Bad Request") and the response states the first empty value.
   *     Values of other types cannot be empty, so where schema validation is also active an empty
   *     value can only occur in a string. The check is applied while the request body is decoded,
   *     so it needs no schema and is also applied, if no schema is available for validating the
   *     request body. A property that the request body omits, or states as null, is left without a
   *     value and is not affected.
   * @langDe Option zur Zurückweisung leerer Werte im Request-Body einer POST- oder PUT-Anfrage. Ein
   *     Wert ist leer, wenn es eine Zeichenkette ohne Zeichen oder nur mit Leerraum ist. Die
   *     Prüfung wird nur auf Anfragen mit einem `Prefer`-Header mit dem Wert "handling=strict"
   *     angewendet; eine solche Anfrage wird mit HTTP 400 ("Bad Request") zurückgewiesen und die
   *     Antwort benennt den ersten leeren Wert. Werte anderer Datentypen können nicht leer sein,
   *     d.h. wenn zusätzlich die Schemavalidierung aktiv ist, kann ein leerer Wert nur in einer
   *     Zeichenkette auftreten. Die Prüfung erfolgt beim Dekodieren des Request-Body, benötigt
   *     daher kein Schema und wird auch angewendet, wenn kein Schema für die Validierung des
   *     Request-Body verfügbar ist. Eine Eigenschaft, die im Request-Body fehlt oder als null
   *     angegeben ist, bleibt ohne Wert und ist nicht betroffen.
   * @default false
   * @since v4.9
   */
  @Nullable
  Boolean getRejectEmptyValues();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean rejectsEmptyValues() {
    return Objects.equals(getRejectEmptyValues(), true);
  }

  /**
   * @langEn Option to enable support for the `return` preference in POST, PUT and PATCH requests. A
   *     request with a `Prefer` header with the value "return=representation" is answered with a
   *     representation of the created or changed feature in the response body; the status code is
   *     201 ("Created") for a new feature, with the URI of the feature in a `Location` header, and
   *     200 ("OK") for a changed feature. The representation is the one that a GET request for the
   *     feature returns, in the coordinate reference system of the `Content-Crs` header of the
   *     request, or in the default coordinate reference system of the collection, if the request
   *     does not include the header. The media type of the representation is negotiated with the
   *     `Accept` header of the request among the feature encodings of the collection; a request
   *     without an `Accept` header, or one that accepts any media type, is answered in the media
   *     type of the request body, if the collection supports it. The response reports the applied
   *     preference in a `Preference-Applied` header. Without the option, or in a request with a
   *     `Prefer` header with the value "return=minimal", the response has no body: 201 with a
   *     `Location` header for a new feature, 204 ("No Content") for a changed feature.
   * @langDe Option zur Aktivierung der Unterstützung für die `return`-Präferenz in POST-, PUT- und
   *     PATCH-Anfragen. Eine Anfrage mit einem `Prefer`-Header mit dem Wert "return=representation"
   *     wird mit einer Repräsentation des erzeugten oder geänderten Features im Response-Body
   *     beantwortet; der Statuscode ist 201 ("Created") bei einem neuen Feature, mit der URI des
   *     Features in einem `Location`-Header, und 200 ("OK") bei einem geänderten Feature. Die
   *     Repräsentation ist diejenige, die eine GET-Anfrage auf das Feature zurückgibt, im
   *     Koordinatenreferenzsystem des `Content-Crs`-Headers der Anfrage bzw. im
   *     Standard-Koordinatenreferenzsystem der Collection, wenn die Anfrage den Header nicht
   *     enthält. Der Media-Type der Repräsentation wird über den `Accept`-Header der Anfrage aus
   *     den Feature-Formaten der Collection ermittelt; eine Anfrage ohne `Accept`-Header oder mit
   *     einem beliebigen Media-Type wird im Media-Type des Request-Body beantwortet, sofern die
   *     Collection diesen unterstützt. Die Antwort meldet die angewendete Präferenz in einem
   *     `Preference-Applied`-Header. Ohne die Option oder bei einer Anfrage mit einem
   *     `Prefer`-Header mit dem Wert "return=minimal" hat die Antwort keinen Body: 201 mit einem
   *     `Location`-Header bei einem neuen Feature, 204 ("No Content") bei einem geänderten Feature.
   * @default false
   * @since v4.9
   */
  @Nullable
  Boolean getReturnRepresentation();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean returnsRepresentation() {
    return Objects.equals(getReturnRepresentation(), true);
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableCrudConfiguration.Builder();
  }
}
