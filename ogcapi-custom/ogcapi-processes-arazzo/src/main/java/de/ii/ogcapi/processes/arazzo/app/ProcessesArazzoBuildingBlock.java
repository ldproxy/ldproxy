/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.processes.arazzo.domain.ImmutableProcessesArazzoConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title Processes - Arazzo
 * @langEn Processes that run workflows described with Arazzo.
 * @langDe Prozesse, die mit Arazzo beschriebene Workflows ausführen.
 * @scopeEn The building block runs workflows of
 *     [Arazzo](https://spec.openapis.org/arazzo/latest.html) descriptions (versions 1.0 and 1.1) as
 *     processes of the building block [Processes](processes.md). A workflow calls operations of the
 *     APIs of this server one after the other, the results of a step are available to the following
 *     steps.
 *     <p>There are two ways to publish workflows:
 *     <p><code>
 *     - Stored workflows: each workflow of an Arazzo description in `values/workflows/{apiId}` is a
 *       process with the workflow id as its id. The inputs of the process are the properties of the
 *       input schema of the workflow, the outputs are the outputs of the workflow.
 *     - Ad hoc: a process description in `values/processes/{apiId}` with the step
 *       `arazzo-document:execute` in `job.partials` takes an Arazzo description as the input
 *       `document`, optionally `workflowId` and the `inputs` of the workflow. The outputs are
 *       `outputs`, the outputs of the workflow, and `steps`, the status code, the success and the
 *       outputs of each step that ran.
 *     </code>
 *     <p>The supported subset of Arazzo:
 *     <p><code>
 *     - Source descriptions of the type `openapi` must be the OpenAPI document of an API of this
 *       server (`/{apiId}/api`); external APIs are not supported. Source descriptions of the type
 *       `arazzo` refer to stored descriptions of the API by file name.
 *     - Steps with `operationId`, `operationPath` or `workflowId`. The operations that a step can
 *       call are: the features of a collection and a feature
 *       (`/collections/{collectionId}/items[/{featureId}]`), stored queries (`GET` and `POST
 *       /search/{queryId}`), ad-hoc queries (`POST /search`) and the execution of a process (`POST
 *       /processes/{processId}/execution`). The responses are JSON.
 *     - Parameters in `path`, `query` and `header`, request bodies with JSON content and
 *       replacements with JSON Pointers.
 *     - Success criteria of the types `simple` and `regex`; without criteria, a step is successful
 *       with a status code 2xx.
 *     - The actions `end`, `goto` and `retry` with `retryAfter` (at most 60 seconds) and
 *       `retryLimit`, also as reusable components.
 *     - All runtime expressions except `$message` and `$self`; outputs are runtime expressions.
 *     </code>
 *     <p>Not supported are AsyncAPI, selector objects, `jsonpath` and `xpath` criteria,
 *     `dependsOn`, `timeout` and parameters of actions. A description that uses them is rejected; a
 *     stored description is then not published, the problems are logged.
 *     <p>A workflow runs in a job and holds it while it runs. A step that executes a process waits
 *     for the job of the process; this needs `jobs.maxConcurrent` of at least 2 in `cfg.yml`. At
 *     most `jobs.maxConcurrent - 1` workflows run at the same time, so that a job stays free for
 *     the processes they execute; a further execution of a workflow fails at once with the status
 *     429, the client retries later.
 *     <p>A step has the permissions of a request without credentials. The input schema of a stored
 *     workflow may only use the keywords of process descriptions, annotations such as `default` and
 *     `examples` are allowed; `default` values apply to missing inputs.
 * @scopeDe Der Baustein führt Workflows aus
 *     [Arazzo](https://spec.openapis.org/arazzo/latest.html)-Beschreibungen (Versionen 1.0 und 1.1)
 *     als Prozesse des Bausteins [Processes](processes.md) aus. Ein Workflow ruft nacheinander
 *     Operationen der APIs dieses Servers auf, die Ergebnisse eines Schritts stehen den folgenden
 *     Schritten zur Verfügung.
 *     <p>Workflows werden auf zwei Arten veröffentlicht:
 *     <p><code>
 *     - Gespeicherte Workflows: Jeder Workflow einer Arazzo-Beschreibung in
 *       `values/workflows/{apiId}` ist ein Prozess mit der Workflow-Id als Id. Die Eingaben des
 *       Prozesses sind die Eigenschaften des Eingabeschemas des Workflows, die Ausgaben die Ausgaben
 *       des Workflows.
 *     - Ad hoc: Eine Prozessbeschreibung in `values/processes/{apiId}` mit dem Schritt
 *       `arazzo-document:execute` in `job.partials` erhält eine Arazzo-Beschreibung als Eingabe
 *       `document`, optional `workflowId` und die Eingaben `inputs` des Workflows. Die Ausgaben sind
 *       `outputs`, die Ausgaben des Workflows, und `steps`, der Statuscode, der Erfolg und die
 *       Ausgaben jedes ausgeführten Schritts.
 *     </code>
 *     <p>Die unterstützte Teilmenge von Arazzo:
 *     <p><code>
 *     - Quellbeschreibungen vom Typ `openapi` müssen das OpenAPI-Dokument einer API dieses Servers
 *       sein (`/{apiId}/api`); externe APIs werden nicht unterstützt. Quellbeschreibungen vom Typ
 *       `arazzo` verweisen über den Dateinamen auf gespeicherte Beschreibungen der API.
 *     - Schritte mit `operationId`, `operationPath` oder `workflowId`. Die Operationen, die ein
 *       Schritt aufrufen kann, sind: die Features einer Collection und ein Feature
 *       (`/collections/{collectionId}/items[/{featureId}]`), gespeicherte Abfragen (`GET` und `POST
 *       /search/{queryId}`), Ad-hoc-Abfragen (`POST /search`) und die Ausführung eines Prozesses
 *       (`POST /processes/{processId}/execution`). Die Antworten sind JSON.
 *     - Parameter in `path`, `query` und `header`, Anfrageinhalte in JSON und Ersetzungen mit JSON
 *       Pointern.
 *     - Erfolgskriterien vom Typ `simple` und `regex`; ohne Kriterien ist ein Schritt bei einem
 *       Statuscode 2xx erfolgreich.
 *     - Die Aktionen `end`, `goto` und `retry` mit `retryAfter` (höchstens 60 Sekunden) und
 *       `retryLimit`, auch als wiederverwendbare Komponenten.
 *     - Alle Laufzeitausdrücke außer `$message` und `$self`; Ausgaben sind Laufzeitausdrücke.
 *     </code>
 *     <p>Nicht unterstützt werden AsyncAPI, Selektor-Objekte, Kriterien vom Typ `jsonpath` und
 *     `xpath`, `dependsOn`, `timeout` und Parameter von Aktionen. Eine Beschreibung, die sie
 *     verwendet, wird abgelehnt; eine gespeicherte Beschreibung wird dann nicht veröffentlicht, die
 *     Probleme werden protokolliert.
 *     <p>Ein Workflow läuft in einem Job und belegt ihn, solange er läuft. Ein Schritt, der einen
 *     Prozess ausführt, wartet auf den Job des Prozesses; dafür muss `jobs.maxConcurrent` in
 *     `cfg.yml` mindestens 2 sein. Höchstens `jobs.maxConcurrent - 1` Workflows laufen
 *     gleichzeitig, damit ein Job für die Prozesse frei bleibt, die sie ausführen; eine weitere
 *     Ausführung eines Workflows schlägt sofort mit dem Status 429 fehl, der Client wiederholt sie
 *     später.
 *     <p>Ein Schritt hat die Berechtigungen einer Anfrage ohne Anmeldedaten. Das Eingabeschema
 *     eines gespeicherten Workflows darf nur die Schlüsselwörter von Prozessbeschreibungen
 *     verwenden, Annotationen wie `default` und `examples` sind erlaubt; `default`-Werte gelten für
 *     fehlende Eingaben.
 * @ref:cfg {@link de.ii.ogcapi.processes.arazzo.domain.ProcessesArazzoConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.processes.arazzo.domain.ImmutableProcessesArazzoConfiguration}
 */
@Singleton
@AutoBind
public class ProcessesArazzoBuildingBlock implements ApiBuildingBlock {

  public static final int MAXIMUM_STEPS = 100;
  public static final int MAXIMUM_RESPONSE_SIZE = 10_000_000;

  @Inject
  public ProcessesArazzoBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProcessesArazzoConfiguration.Builder()
        .enabled(false)
        .maximumSteps(MAXIMUM_STEPS)
        .maximumResponseSize(MAXIMUM_RESPONSE_SIZE)
        .build();
  }
}
