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

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableCrudConfiguration.Builder();
  }
}
