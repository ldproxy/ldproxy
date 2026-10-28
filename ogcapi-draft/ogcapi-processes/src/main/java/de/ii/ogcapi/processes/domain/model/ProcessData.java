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
   * @langEn Whether the job of an execution starts with a setup step. The setup step queues the
   *     step that executes the process. Without it, the execution step is queued directly, e.g. for
   *     processors outside of ldproxy that only handle the step `oap:{processId}:execute`.
   * @langDe Ob der Job einer Ausführung mit einem Setup-Schritt beginnt. Der Setup-Schritt reiht
   *     den Schritt ein, der den Prozess ausführt. Ohne ihn wird der Ausführungsschritt direkt
   *     eingereiht, z.B. für Prozessoren außerhalb von ldproxy, die nur den Schritt
   *     `oap:{processId}:execute` verarbeiten.
   * @default true
   */
  @Value.Default
  default boolean getJobSetup() {
    return true;
  }

  /**
   * @langEn Whether the job of an execution ends with a cleanup step `oap:{processId}:cleanup`.
   * @langDe Ob der Job einer Ausführung mit einem Cleanup-Schritt `oap:{processId}:cleanup` endet.
   * @default true
   */
  @Value.Default
  default boolean getJobCleanup() {
    return true;
  }

  abstract class Builder implements ValueBuilder<ProcessData> {}
}
