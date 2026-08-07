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
import jakarta.validation.constraints.Min;
import javax.annotation.Nullable;
import org.immutables.value.Value;

// ToDo: Docs
/**
 * @buildingBlock PROCESSES_CORE
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PROCESSES_CORE")
@JsonDeserialize(builder = ImmutableProcessesCoreConfiguration.Builder.class)
public interface ProcessesCoreConfiguration extends ExtensionConfiguration {

  int DEFAULT_PAGE_SIZE = 10;
  int MINIMUM_PAGE_SIZE = 1;
  int MAXIMUM_PAGE_SIZE = 10_000;

  enum ExecutionMode {
    SYNC,
    ASYNC,
    BOTH
  }

  /**
   * @default false
   * @since 4.9
   */
  @Nullable
  @Override
  Boolean getEnabled();

  /**
   * @default 10
   * @since 4.9
   */
  @Value.Default
  default Integer getDefaultPageSize() {
    return DEFAULT_PAGE_SIZE;
  }

  /**
   * @default 1
   * @since 4.9
   */
  @Value.Default
  default Integer getMinimumPageSize() {
    return MINIMUM_PAGE_SIZE;
  }

  /**
   * @default 10000
   * @since 4.9
   */
  @Value.Default
  default Integer getMaximumPageSize() {
    return MAXIMUM_PAGE_SIZE;
  }

  /**
   * @langEn Indicates how often the callback should be retried on errors. Should be set to `0` if
   *     no retries are desired.
   * @langDe Gibt an, wie oft der Callback bei Fehlern wiederholt werden soll. Sollte auf `0`
   *     gesetzt werden, falls keine Wiederholungen erwünscht sind.
   * @default 3
   * @since 4.9
   */
  @Min(0)
  @Value.Default
  default Integer getCallbackRetries() {
    return 3;
  }

  /**
   * @default SYNC_ONLY
   * @since 4.9
   */
  @Value.Default
  default ExecutionMode getExecutionMode() {
    return ExecutionMode.SYNC;
  }

  @Override
  default Builder getBuilder() {
    return new ImmutableProcessesCoreConfiguration.Builder();
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}
}
