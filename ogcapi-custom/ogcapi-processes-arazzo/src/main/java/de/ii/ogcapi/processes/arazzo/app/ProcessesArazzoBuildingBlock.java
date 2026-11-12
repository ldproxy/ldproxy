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
 *     APIs of this server, and of allowed external APIs, one after the other, the results of a step
 *     are available to the following steps.
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
 *     - Source descriptions of the type `openapi` are the OpenAPI document of an API of this server
 *       (`/{apiId}/api`) or of an external API whose URL is allowed by `allowedExternalUris`.
 *       Source descriptions of the type `arazzo` refer to stored descriptions of the API by file
 *       name.
 *     - Steps with `operationId`, `operationPath` or `workflowId`. The operations of the APIs of
 *       this server that a step can call are: the features of a collection and a feature
 *       (`/collections/{collectionId}/items[/{featureId}]`), stored queries (`GET` and `POST
 *       /search/{queryId}`), ad-hoc queries (`POST /search`) and the execution of a process (`POST
 *       /processes/{processId}/execution`); their responses are JSON. Steps call the GET and POST
 *       operations of external APIs over HTTP, at the server of the OpenAPI document.
 *     - Parameters in `path`, `query` and `header`, request bodies with JSON content and
 *       replacements with JSON Pointers. A query or header parameter without a value, e.g. from an
 *       optional input, is not sent.
 *     - Success criteria of the types `simple`, `regex`, `jsonpath` (RFC 9535) and `xpath` (XPath
 *       1.0, for XML content); without criteria, a step is successful with a status code 2xx. A
 *       JSONPath condition that starts with a filter, e.g. `$[?length(@.features) > 0]`, tests an
 *       object context itself, as in the examples of the specification.
 *     - Selector objects of the types `jsonpath`, `xpath` and `jsonpointer` in outputs,
 *       parameters, payloads and replacements. A selector that selects one node has its value,
 *       several nodes a list of their values, no node no value.
 *     - The actions `end`, `goto` and `retry` with `retryAfter` (at most 60 seconds) and
 *       `retryLimit`, also as reusable components. A failure action `end` ends a nested workflow;
 *       in the workflow of the process, it fails the execution with the status code and the detail
 *       of the failed step.
 *     - All runtime expressions except `$message` and `$self`. The success criteria and the outputs
 *       of a step that calls a workflow see the outputs of that workflow as `$outputs`.
 *     - The extension `x-dso-forEach` of the Arazzo descriptions of the Digitaal Stelsel
 *       Omgevingswet: a step that calls a workflow runs it once per item of `items` (a runtime
 *       expression or a selector object), with the item as the input named in `als`; each output
 *       of the step is then the list of its values. The runs of the outermost loop execute in
 *       parallel, at most `maximumParallelLoopRuns` at the same time; steps that execute a process
 *       still run one after another.
 *     </code>
 *     <p>Not supported are AsyncAPI, `dependsOn`, `timeout` and parameters of actions. A
 *     description that uses them is rejected; a stored description is then not published, the
 *     problems are logged.
 *     <p>A workflow runs in a job and holds it while it runs. A step that executes a process waits
 *     for the job of the process; this needs `jobs.maxConcurrent` of at least 2 in `cfg.yml`. At
 *     most `jobs.maxConcurrent - 1` workflows run at the same time, so that a job stays free for
 *     the processes they execute; a further execution of a workflow fails at once with the status
 *     429, the client retries later.
 *     <p>A step has the permissions of a request without credentials. A request to an external API
 *     has only the headers of the parameters of its step, the credentials of the caller are not
 *     sent, but the server adds the headers of `externalApiHeaders` and sets the inputs of
 *     `serverInputs`, e.g. an API key of the server; both only for stored workflows. The input
 *     schema of a stored workflow may only use the keywords of process descriptions, annotations
 *     such as `default` and `examples` are allowed; `default` values apply to missing inputs.
 *     <p>The OpenAPI documents of external APIs are kept across executions as long as the `max-age`
 *     of their responses allows, or as long as `externalApiDocumentMaxAge` states.
 *     <p>To analyse a workflow, set the logger `de.ii.ogcapi.processes.arazzo` to `DEBUG` (in
 *     `cfg.yml` or at runtime with the task `log-level` of the admin port): each step is logged
 *     with the workflow, the method, the URL, the status code and the duration, the runs of a loop
 *     with the item. With `TRACE`, the content of the request and the beginning of the response are
 *     logged, too. Headers are never logged.
 * @scopeDe Der Baustein führt Workflows aus
 *     [Arazzo](https://spec.openapis.org/arazzo/latest.html)-Beschreibungen (Versionen 1.0 und 1.1)
 *     als Prozesse des Bausteins [Processes](processes.md) aus. Ein Workflow ruft nacheinander
 *     Operationen der APIs dieses Servers und erlaubter externer APIs auf, die Ergebnisse eines
 *     Schritts stehen den folgenden Schritten zur Verfügung.
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
 *     - Quellbeschreibungen vom Typ `openapi` sind das OpenAPI-Dokument einer API dieses Servers
 *       (`/{apiId}/api`) oder einer externen API, deren URL `allowedExternalUris` erlaubt.
 *       Quellbeschreibungen vom Typ `arazzo` verweisen über den Dateinamen auf gespeicherte
 *       Beschreibungen der API.
 *     - Schritte mit `operationId`, `operationPath` oder `workflowId`. Die Operationen der APIs
 *       dieses Servers, die ein Schritt aufrufen kann, sind: die Features einer Collection und ein Feature
 *       (`/collections/{collectionId}/items[/{featureId}]`), gespeicherte Abfragen (`GET` und `POST
 *       /search/{queryId}`), Ad-hoc-Abfragen (`POST /search`) und die Ausführung eines Prozesses
 *       (`POST /processes/{processId}/execution`); ihre Antworten sind JSON. Die GET- und
 *       POST-Operationen externer APIs rufen Schritte über HTTP auf, beim Server des
 *       OpenAPI-Dokuments.
 *     - Parameter in `path`, `query` und `header`, Anfrageinhalte in JSON und Ersetzungen mit JSON
 *       Pointern. Ein Query- oder Header-Parameter ohne Wert, z.B. aus einer optionalen Eingabe,
 *       wird nicht gesendet.
 *     - Erfolgskriterien vom Typ `simple`, `regex`, `jsonpath` (RFC 9535) und `xpath` (XPath 1.0,
 *       für XML-Inhalte); ohne Kriterien ist ein Schritt bei einem Statuscode 2xx erfolgreich. Eine
 *       JSONPath-Bedingung, die mit einem Filter beginnt, z.B. `$[?length(@.features) > 0]`, prüft
 *       einen Objekt-Kontext selbst, wie in den Beispielen der Spezifikation.
 *     - Selektor-Objekte vom Typ `jsonpath`, `xpath` und `jsonpointer` in Ausgaben, Parametern,
 *       Anfrageinhalten und Ersetzungen. Ein Selektor, der einen Knoten auswählt, hat dessen Wert,
 *       bei mehreren Knoten die Liste ihrer Werte, ohne Knoten keinen Wert.
 *     - Die Aktionen `end`, `goto` und `retry` mit `retryAfter` (höchstens 60 Sekunden) und
 *       `retryLimit`, auch als wiederverwendbare Komponenten. Eine Fehleraktion `end` beendet einen
 *       verschachtelten Workflow; im Workflow des Prozesses lässt sie die Ausführung mit dem
 *       Statuscode und dem Detail des fehlgeschlagenen Schritts fehlschlagen.
 *     - Alle Laufzeitausdrücke außer `$message` und `$self`. Die Erfolgskriterien und die Ausgaben
 *       eines Schritts, der einen Workflow aufruft, sehen die Ausgaben dieses Workflows als
 *       `$outputs`.
 *     - Die Erweiterung `x-dso-forEach` der Arazzo-Beschreibungen des Digitaal Stelsel
 *       Omgevingswet: Ein Schritt, der einen Workflow aufruft, führt ihn für jedes Element von
 *       `items` aus (ein Laufzeitausdruck oder ein Selektor-Objekt), mit dem Element als der in
 *       `als` genannten Eingabe; jede Ausgabe des Schritts ist dann die Liste ihrer Werte. Die
 *       Durchläufe der äußersten Schleife werden parallel ausgeführt, höchstens
 *       `maximumParallelLoopRuns` gleichzeitig; Schritte, die einen Prozess ausführen, laufen
 *       weiterhin nacheinander.
 *     </code>
 *     <p>Nicht unterstützt werden AsyncAPI, `dependsOn`, `timeout` und Parameter von Aktionen. Eine
 *     Beschreibung, die sie verwendet, wird abgelehnt; eine gespeicherte Beschreibung wird dann
 *     nicht veröffentlicht, die Probleme werden protokolliert.
 *     <p>Ein Workflow läuft in einem Job und belegt ihn, solange er läuft. Ein Schritt, der einen
 *     Prozess ausführt, wartet auf den Job des Prozesses; dafür muss `jobs.maxConcurrent` in
 *     `cfg.yml` mindestens 2 sein. Höchstens `jobs.maxConcurrent - 1` Workflows laufen
 *     gleichzeitig, damit ein Job für die Prozesse frei bleibt, die sie ausführen; eine weitere
 *     Ausführung eines Workflows schlägt sofort mit dem Status 429 fehl, der Client wiederholt sie
 *     später.
 *     <p>Ein Schritt hat die Berechtigungen einer Anfrage ohne Anmeldedaten. Eine Anfrage an eine
 *     externe API hat nur die Header aus den Parametern ihres Schritts, die Anmeldedaten des
 *     Aufrufers werden nicht gesendet, aber der Server fügt die Header aus `externalApiHeaders`
 *     hinzu und setzt die Eingaben aus `serverInputs`, z.B. einen API-Schlüssel des Servers; beides
 *     nur für gespeicherte Workflows. Das Eingabeschema eines gespeicherten Workflows darf nur die
 *     Schlüsselwörter von Prozessbeschreibungen verwenden, Annotationen wie `default` und
 *     `examples` sind erlaubt; `default`-Werte gelten für fehlende Eingaben.
 *     <p>Die OpenAPI-Dokumente externer APIs werden über Ausführungen hinweg behalten, so lange es
 *     `max-age` ihrer Antworten erlaubt oder `externalApiDocumentMaxAge` angibt.
 *     <p>Zur Analyse eines Workflows wird der Logger `de.ii.ogcapi.processes.arazzo` auf `DEBUG`
 *     gesetzt (in `cfg.yml` oder zur Laufzeit mit dem Task `log-level` des Admin-Ports): Jeder
 *     Schritt wird mit dem Workflow, der Methode, der URL, dem Statuscode und der Dauer
 *     protokolliert, die Durchläufe einer Schleife mit dem Element. Mit `TRACE` werden auch der
 *     Inhalt der Anfrage und der Anfang der Antwort protokolliert. Header werden nie protokolliert.
 * @ref:cfg {@link de.ii.ogcapi.processes.arazzo.domain.ProcessesArazzoConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.processes.arazzo.domain.ImmutableProcessesArazzoConfiguration}
 */
@Singleton
@AutoBind
public class ProcessesArazzoBuildingBlock implements ApiBuildingBlock {

  public static final int MAXIMUM_STEPS = 100;
  public static final int MAXIMUM_RESPONSE_SIZE = 10_000_000;
  public static final int MAXIMUM_PARALLEL_LOOP_RUNS = 4;

  @Inject
  public ProcessesArazzoBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProcessesArazzoConfiguration.Builder()
        .enabled(false)
        .maximumSteps(MAXIMUM_STEPS)
        .maximumResponseSize(MAXIMUM_RESPONSE_SIZE)
        .maximumParallelLoopRuns(MAXIMUM_PARALLEL_LOOP_RUNS)
        .build();
  }
}
