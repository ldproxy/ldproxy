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
   *     `If-Unmodified-Since` header, otherwise they will be rejected. A feature will only be
   *     changed, if the feature was not changed since the timestamp in the header (or if no last
   *     modification time is known for the feature). The last modification time of a feature is
   *     determined from a feature property with type `DATETIME` for which `isLastModified` is set
   *     to true in the schema in the feature provider.
   * @langDe Option zur Aktivierung der Unterstützung für die bedingte Verarbeitung von PUT-, PATCH-
   *     und DELETE-Anfragen, basierend auf der Zeit, zu der das Feature zuletzt aktualisiert wurde.
   *     Solche Anfragen müssen einen `If-Unmodified-Since`-Header enthalten, andernfalls werden sie
   *     zurückgewiesen. Ein Feature wird nur dann geändert, wenn das Feature seit dem Zeitstempel
   *     im Header nicht geändert wurde (oder wenn kein letzter Änderungszeitpunkt für das Feature
   *     bekannt ist). Der Zeitpunkt der letzten Änderung eines Features wird anhand einer
   *     Objekteigenschaft mit Datentyp `DATETIME` ermittelt, für die `isLastModified` im Schema des
   *     Feature Providers auf `true` gesetzt ist.
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

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableCrudConfiguration.Builder();
  }
}
