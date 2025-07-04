/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.codelist.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock PROFILE_CODELIST
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: PROFILE_CODELIST
 *   enabled: true
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PROFILE_CODELIST")
@JsonDeserialize(builder = ImmutableProfileCodelistConfiguration.Builder.class)
public interface ProfileCodelistConfiguration extends ExtensionConfiguration {

  /**
   * @default true
   */
  @Nullable
  @Override
  Boolean getEnabled();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableProfileCodelistConfiguration.Builder();
  }
}
