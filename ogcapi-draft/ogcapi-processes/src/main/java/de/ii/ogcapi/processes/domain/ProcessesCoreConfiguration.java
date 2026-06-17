/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import javax.annotation.Nullable;
import org.immutables.value.Value;

// ToDo Docs
/**
 * @buildingBlock PROCESSES_CORE
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PROCESSES_CORE")
@JsonDeserialize(builder = ImmutableProcessesCoreConfiguration.Builder.class)
public interface ProcessesCoreConfiguration extends ExtensionConfiguration {

  /**
   * @default false
   */
  @Nullable
  @Override
  Boolean getEnabled();

  @Override
  default Builder getBuilder() {
    return new ImmutableProcessesCoreConfiguration.Builder();
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}
}
