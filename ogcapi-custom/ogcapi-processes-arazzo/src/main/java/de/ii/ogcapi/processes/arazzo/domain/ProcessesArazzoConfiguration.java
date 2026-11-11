/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock PROCESSES_ARAZZO
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: PROCESSES_ARAZZO
 *   enabled: true
 *   maximumSteps: 50
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PROCESSES_ARAZZO")
@JsonDeserialize(builder = ImmutableProcessesArazzoConfiguration.Builder.class)
public interface ProcessesArazzoConfiguration extends ExtensionConfiguration {

  /**
   * @langEn The maximum number of steps that one execution of a workflow runs, including the steps
   *     of nested workflows and the steps that run again after `goto` or `retry`. An execution that
   *     needs more steps fails.
   * @langDe Die maximale Anzahl an Schritten, die eine Ausführung eines Workflows durchführt,
   *     einschließlich der Schritte verschachtelter Workflows und der Schritte, die nach `goto`
   *     oder `retry` erneut ausgeführt werden. Eine Ausführung, die mehr Schritte benötigt, schlägt
   *     fehl.
   * @default 100
   * @since v5.0
   */
  @Nullable
  Integer getMaximumSteps();

  /**
   * @langEn The maximum size in bytes of the response of a step. A step with a larger response
   *     fails, the request of the step needs a smaller result, e.g. with a smaller `limit`.
   * @langDe Die maximale Größe der Antwort eines Schritts in Bytes. Ein Schritt mit einer größeren
   *     Antwort schlägt fehl, die Anfrage des Schritts benötigt ein kleineres Ergebnis, z.B. mit
   *     einem kleineren `limit`.
   * @default 10000000
   * @since v5.0
   */
  @Nullable
  Integer getMaximumResponseSize();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableProcessesArazzoConfiguration.Builder();
  }
}
