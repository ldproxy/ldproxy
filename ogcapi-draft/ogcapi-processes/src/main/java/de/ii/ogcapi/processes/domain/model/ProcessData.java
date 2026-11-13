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
import de.ii.xtraplatform.docs.DocFile;
import de.ii.xtraplatform.docs.DocStep;
import de.ii.xtraplatform.docs.DocStep.Step;
import de.ii.xtraplatform.docs.DocTable;
import de.ii.xtraplatform.docs.DocTable.ColumnSet;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import org.immutables.value.Value;

/**
 * @langEn # Processes
 *     <p>Process descriptions define the processes that the building block
 *     [Processes](../services/building-blocks/processes.md) publishes and executes.
 * @langDe # Prozesse
 *     <p>Prozessbeschreibungen definieren die Prozesse, die der Baustein
 *     [Processes](../services/building-blocks/processes.md) veröffentlicht und ausführt.
 * @langEn ## Configuration
 *     <p>A process description follows the requirements class [OGC PROCESS
 *     DESCRIPTION](https://docs.ogc.org/DRAFTS/18-062r3.html#clause_process-description), e.g. with
 *     `id`, `title`, `version`, `jobControlOptions`, `inputs` and `outputs`.
 *     <p>Additionally, the option `job` defines the job that runs an execution. Each step of the
 *     job is queued as a partial job of kind `oap:{processId}:{step}`, to be processed by ldproxy
 *     or by a processor outside of ldproxy, e.g. an xtralink workflow. All options of `job` are
 *     optional:
 *     <p>{@docTable:job}
 *     <p>The option `readOnly` states that an execution only reads data and has no other effects
 *     (default: `false`).
 * @langDe ## Konfiguration
 *     <p>Eine Prozessbeschreibung folgt der Anforderungsklasse [OGC PROCESS
 *     DESCRIPTION](https://docs.ogc.org/DRAFTS/18-062r3.html#clause_process-description), z.B. mit
 *     `id`, `title`, `version`, `jobControlOptions`, `inputs` und `outputs`.
 *     <p>Zusätzlich legt die Option `job` den Job fest, der eine Ausführung durchführt. Jeder
 *     Schritt des Jobs wird als Teil-Job der Art `oap:{processId}:{schritt}` eingereiht und von
 *     ldproxy oder einem Prozessor außerhalb von ldproxy verarbeitet, z.B. einem xtralink-Workflow.
 *     Alle Optionen von `job` sind optional:
 *     <p>{@docTable:job}
 *     <p>Die Option `readOnly` gibt an, dass eine Ausführung nur Daten liest und keine anderen
 *     Wirkungen hat (Standard: `false`).
 * @langEn ### Example
 *     <p>A process `Echo` that returns its input, executed by a single step
 *     `oap:EchoProcess:execute`:
 * @langDe ### Beispiel
 *     <p>Ein Prozess `Echo`, der seine Eingabe zurückgibt und von einem einzigen Schritt
 *     `oap:EchoProcess:execute` ausgeführt wird:
 * @langAll <code>
 * ```yaml
 * ---
 * id: EchoProcess
 * title: Echo Process
 * description: This process simply echoes its input as an output.
 * version: 1.0.0
 * jobControlOptions: [SYNC_EXECUTE, ASYNC_EXECUTE]
 * inputs:
 *    inputString:
 *      title: Input String
 *      schema:
 *        type: string
 * outputs:
 *    inputString:
 *      title: Input String
 *      schema:
 *        type: string
 * job:
 *    partials: [execute]
 * ```
 * </code>
 * @langEn ## Storage
 *     <p>Process descriptions reside in the
 *     [Store](../application/20-configuration/10-store-new.md) as values with type `processes`.
 *     Global process descriptions are stored in `values/processes`, process descriptions for a
 *     specific API in `values/processes/{apiId}`.
 * @langDe ## Speicherung
 *     <p>Prozessbeschreibungen liegen im [Store](../application/20-configuration/10-store-new.md)
 *     als Values mit Typ `processes`. Globale Prozessbeschreibungen liegen in `values/processes`,
 *     Prozessbeschreibungen für eine bestimmte API in `values/processes/{apiId}`.
 * @ref:jobProperties {@link de.ii.ogcapi.processes.domain.model.ImmutableProcessJobOptions}
 */
@DocFile(
    path = "auxiliaries",
    name = "processes.md",
    tables = {
      @DocTable(
          name = "job",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:jobProperties}"),
            @DocStep(type = Step.JSON_PROPERTIES)
          },
          columnSet = ColumnSet.JSON_PROPERTIES)
    })
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "processes")
@JsonInclude(Include.NON_EMPTY)
@JsonDeserialize(builder = ImmutableProcessData.Builder.class)
public interface ProcessData extends StoredValue, OgcProcess {

  /**
   * @langEn The steps of the job that runs an execution.
   * @langDe Die Schritte des Jobs, der eine Ausführung durchführt.
   * @default {}
   */
  @Value.Default
  default ProcessJobOptions getJob() {
    return new ImmutableProcessJobOptions.Builder().build();
  }

  /**
   * @langEn Whether an execution only reads data and has no other effects. Clients can then execute
   *     the process like a query, e.g. as an MCP tool that is marked as read-only.
   * @langDe Ob eine Ausführung nur Daten liest und keine anderen Wirkungen hat. Clients können den
   *     Prozess dann wie eine Abfrage ausführen, z.B. als MCP-Tool, das als nur lesend markiert
   *     ist.
   * @default false
   * @since 4.9
   */
  @Value.Default
  default boolean getReadOnly() {
    return false;
  }

  abstract class Builder implements ValueBuilder<ProcessData> {}
}
