/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "processes")
@JsonInclude(Include.NON_EMPTY)
@JsonDeserialize(builder = ImmutableProcessData.Builder.class)
public interface ProcessData extends StoredValue, OgcProcess {

  /**
   * @langEn Whether the job of an execution starts with a setup step `oap:{processId}:setup`. The
   *     step `oap:{processId}:execute` is then queued once setup succeeded, otherwise directly.
   * @langDe Ob der Job einer Ausführung mit einem Setup-Schritt `oap:{processId}:setup` beginnt.
   *     Der Schritt `oap:{processId}:execute` wird dann eingereiht, sobald das Setup erfolgreich
   *     war, sonst direkt.
   * @default false
   */
  @Value.Default
  default boolean getJobSetup() {
    return false;
  }

  /**
   * @langEn Whether the job of an execution ends with a cleanup step `oap:{processId}:cleanup`.
   * @langDe Ob der Job einer Ausführung mit einem Cleanup-Schritt `oap:{processId}:cleanup` endet.
   * @default false
   */
  @Value.Default
  default boolean getJobCleanup() {
    return false;
  }

  abstract class Builder implements ValueBuilder<ProcessData> {}
}
