/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.CachingConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.Objects;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock SEARCH
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: SEARCH
 *   enabled: true
 *   managerEnabled: true
 *   validationEnabled: false
 *   allLinksAreLocal: true
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "SEARCH")
@JsonDeserialize(builder = ImmutableSearchConfiguration.Builder.class)
public interface SearchConfiguration extends ExtensionConfiguration, CachingConfiguration {

  /**
   * @langEn Option to manage stored queries using PUT and DELETE.
   * @langDe Steuert, ob Stored Queries über PUT und DELETE verwaltet werden können.
   * @default false
   * @since v3.4
   */
  @Nullable
  Boolean getManagerEnabled();

  @Nullable
  Boolean getOptimisticLockingLastModified();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean supportsLastModified() {
    return Objects.equals(getOptimisticLockingLastModified(), true);
  }

  @Nullable
  Boolean getOptimisticLockingETag();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean supportsEtag() {
    return Objects.equals(getOptimisticLockingETag(), true);
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean isManagerEnabled() {
    return Objects.equals(getManagerEnabled(), true);
  }

  /**
   * @langEn Option to validate stored queries when using PUT by setting a `Prefer` header with
   *     `handling=strict`.
   * @langDe Steuert, ob bei PUT von Stored Queries die Validierung über den Header `Prefer` (Wert
   *     `handling=strict`) unterstützt werden soll.
   * @default false
   * @since v3.4
   */
  @Nullable
  Boolean getValidationEnabled();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean isValidationEnabled() {
    return Objects.equals(getValidationEnabled(), true);
  }

  /**
   * @langEn Signals feature encoders whether all link targets are within the same document.
   * @langDe Signalisiert Feature-Encodern, ob alle Links auf Objekte im selben Dokuments zeigen.
   * @default false
   * @since v3.4
   */
  @Nullable
  Boolean getAllLinksAreLocal();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean linksAreLocal() {
    return Objects.equals(getAllLinksAreLocal(), true);
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableSearchConfiguration.Builder().from(this);
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    ImmutableSearchConfiguration.Builder builder =
        new ImmutableSearchConfiguration.Builder().from(source).from(this);

    return builder.build();
  }
}
