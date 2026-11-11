/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import java.util.List;
import org.immutables.value.Value;

/**
 * @langEn The steps of the job that runs an execution. Each step is queued as a partial job of kind
 *     `oap:{processId}:{step}`, to be processed by ldproxy or by a processor outside of ldproxy,
 *     e.g. an xtralink workflow.
 * @langDe Die Schritte des Jobs, der eine Ausführung durchführt. Jeder Schritt wird als Teil-Job
 *     der Art `oap:{processId}:{schritt}` eingereiht und von ldproxy oder einem Prozessor außerhalb
 *     von ldproxy verarbeitet, z.B. einem xtralink-Workflow.
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDeserialize(builder = ImmutableProcessJobOptions.Builder.class)
public interface ProcessJobOptions {

  /**
   * @langEn Whether the job starts with the step `setup`. The steps in `partials` are then queued
   *     once it succeeded.
   * @langDe Ob der Job mit dem Schritt `setup` beginnt. Die Schritte in `partials` werden dann
   *     eingereiht, sobald er erfolgreich war.
   * @default false
   * @since 4.9
   */
  @Value.Default
  default boolean getSetup() {
    return false;
  }

  /**
   * @langEn The steps that execute the process, e.g. `[transform, execute]`. At least one step is
   *     required.
   * @langDe Die Schritte, die den Prozess ausführen, z.B. `[transform, execute]`. Mindestens ein
   *     Schritt ist erforderlich.
   * @default [execute]
   * @since 4.9
   */
  @Value.Default
  default List<String> getPartials() {
    return List.of("execute");
  }

  /**
   * @langEn Whether the steps in `partials` run one after the other in the given order, each only
   *     once the previous one succeeded. Otherwise they run in parallel.
   * @langDe Ob die Schritte in `partials` nacheinander in der angegebenen Reihenfolge laufen, jeder
   *     erst, wenn der vorige erfolgreich war. Sonst laufen sie parallel.
   * @default false
   * @since 4.9
   */
  @Value.Default
  default boolean getSequential() {
    return false;
  }

  /**
   * @langEn Whether the job ends with the step `cleanup`, which also runs after a failure.
   * @langDe Ob der Job mit dem Schritt `cleanup` endet, der auch nach einem Fehler läuft.
   * @default false
   * @since 4.9
   */
  @Value.Default
  default boolean getCleanup() {
    return false;
  }

  @Value.Check
  default void check() {
    Preconditions.checkState(
        !getPartials().isEmpty(), "A process job needs at least one partial, e.g. 'execute'.");
  }
}
