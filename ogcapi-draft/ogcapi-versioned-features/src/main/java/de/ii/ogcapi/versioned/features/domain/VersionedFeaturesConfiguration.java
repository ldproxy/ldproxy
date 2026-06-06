/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock VERSIONED_FEATURES
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: VERSIONED_FEATURES
 *   enabled: true
 *   timeAxis: VALIDITY_TIME
 *   mutationTime: SERVER
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "VERSIONED_FEATURES")
@JsonDeserialize(builder = ImmutableVersionedFeaturesConfiguration.Builder.class)
public interface VersionedFeaturesConfiguration extends ExtensionConfiguration {

  /**
   * Time axis along which version intervals are interpreted.
   *
   * <p>{@code VALIDITY_TIME} models the real-world validity of a feature; {@code TRANSACTION_TIME}
   * models when the version was recorded in the system.
   */
  enum TimeAxis {
    VALIDITY_TIME,
    TRANSACTION_TIME
  }

  /**
   * Source of the timestamp used when a mutation creates or retires a version.
   *
   * <p>{@code SERVER} uses the request timestamp captured by the server; {@code CLIENT} uses a
   * timestamp supplied by the client (in the request body or via an explicit header).
   */
  enum MutationTime {
    SERVER,
    CLIENT
  }

  /**
   * @langEn Time axis along which the version intervals of a collection are interpreted. {@code
   *     VALIDITY_TIME} (the validity of the feature in the real world) or {@code TRANSACTION_TIME}
   *     (when the version was recorded in the system). Must be set when the building block is
   *     enabled for a collection.
   * @langDe Zeitachse, entlang derer die Versionsintervalle einer Sammlung interpretiert werden.
   *     {@code VALIDITY_TIME} (die Gültigkeit des Features in der realen Welt) oder {@code
   *     TRANSACTION_TIME} (Zeitpunkt der Erfassung im System). Muss gesetzt sein, wenn der Baustein
   *     für eine Sammlung aktiviert ist.
   * @default null
   * @since v4.10
   */
  @Nullable
  TimeAxis getTimeAxis();

  /**
   * @langEn Source of the timestamp used when a mutation creates or retires a version. {@code
   *     SERVER} uses the request timestamp captured by the server; {@code CLIENT} uses a timestamp
   *     supplied by the client. Must be set when the collection accepts mutations.
   * @langDe Quelle des Zeitstempels, der beim Erzeugen oder Stilllegen einer Version verwendet
   *     wird. {@code SERVER} verwendet den serverseitig erfassten Anfragezeitstempel; {@code
   *     CLIENT} verwendet einen vom Client gelieferten Zeitstempel. Muss gesetzt sein, wenn die
   *     Sammlung Mutationen akzeptiert.
   * @default null
   * @since v4.10
   */
  @Nullable
  MutationTime getMutationTime();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableVersionedFeaturesConfiguration.Builder();
  }

  // Scalar-only options at present; explicit override so that list-fields added later will
  // concat+distinct instead of replacing the source value on merge.
  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableVersionedFeaturesConfiguration.Builder().from(source).from(this).build();
  }
}
