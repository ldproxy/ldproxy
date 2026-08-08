/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.processes.domain.ImmutableProcessesCoreConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * @title Processes
 * @langEn Retrieve descriptions of supported processes and execute them.
 * @langDe Beschreibungen von unterstützten Prozessen abrufen und diese ausführen.
 * @scopeEn *Processes* allows describing, retrieving and executing processes.
 *     <p>Process descriptions are defined using the requirements class [OGC PROCESS
 *     DESCRIPTION](https://docs.ogc.org/DRAFTS/18-062r3.html#ogc_process_description). They can be
 *     defined globally and on a per-API basis.
 *     <p>This building block supports both synchronous and asynchronous execution of processes.
 *     Using the [configuration](#configuration), it is possible to limit support to only
 *     synchronous or asynchronous execution, or to support both modes.
 *     <p>Some limitations apply; see the [Limitations](#limitations) section.
 * @scopeDe *Processes* ermöglicht die Beschreibung, den Abruf und die Ausführung von Prozessen.
 *     <p>Prozessbeschreibungen werden mithilfe der Anforderungsklasse [OGC PROCESS
 *     DESCRIPTION](https://docs.ogc.org/DRAFTS/18-062r3.html#ogc_process_description) definiert.
 *     Sie können global und pro API definiert werden.
 *     <p>Dieser Baustein unterstützt sowohl synchrone als auch asynchrone Ausführung von Prozessen.
 *     Über die [Konfiguration](#konfiguration) kann die Unterstützung auf nur synchrone oder
 *     asynchrone Ausführung beschränkt oder beide Modi aktiviert werden.
 *     <p>Es gelten einige Einschränkungen; siehe den Abschnitt [Einschränkungen](#limitierungen).
 * @storageEn Process descriptions are stored locally as `values` using the
 *     [Store](https://docs.ldproxy.net/application/20-configuration/10-store-new.html). Global
 *     process descriptions are stored in `values/processes`. API-specific process descriptions must
 *     be stored in `values/processes/{API}`, where `{API}` is the name of the API.
 * @storageDe Prozessbeschreibungen werden lokal als `values` im
 *     [Store](https://docs.ldproxy.net/application/20-configuration/10-store-new.html) gespeichert.
 *     Globale Prozessbeschreibungen werden in `values/processes` abgelegt. API-spezifische
 *     Prozessbeschreibungen müssen in `values/processes/{API}` gespeichert werden, wobei `{API}`
 *     mit dem Namen der API ersetzt werden muss.
 * @limitationsEn This implementation does not cover all details and has some limitations:
 *     <p><code>
 *  - All inputs must be provided inline. References are not supported.
 *  - Inputs and outputs cannot have multiple schemas.
 *  - From the [JSON Schema Validation formats](https://json-schema.org/draft/2020-12/json-schema-validation#name-defined-formats), only `date-time` is supported.
 *  - From the additional [OGC PROCESS DESCRIPTION formats](https://docs.ogc.org/DRAFTS/18-062r3.html#sc-input-schema), only `ogc-bbox` is supported.
 *  - `mediatype`, `encoding` and `schema` are not honored in output selection.
 *  - Binary values are not supported.
 *  - `null` is not supported as an input value.
 *  - [Requirement 50](https://docs.ogc.org/DRAFTS/18-062r3.html#_53418543-8dc0-41f2-28df-366acda4d923) ("0-th" result) is not supported.
 *  - Input descriptions do not support [Data classes](https://docs.ogc.org/DRAFTS/18-062r3.html#sc_data_classes), [Data access APIs](https://docs.ogc.org/DRAFTS/18-062r3.html#sc_data_access_APIs) or [Execution unit requirements](https://docs.ogc.org/DRAFTS/18-062r3.html#sc-execution-unit-requirements).
 *  - Output descriptions do not support [Data classes](https://docs.ogc.org/DRAFTS/18-062r3.html#_e1f23667-1a0e-bd37-a05b-c9724b59cb48) or [Data access APIs](https://docs.ogc.org/DRAFTS/18-062r3.html#_e6b07e9e-1559-78f6-c59c-7d7ca363fafb).
 *  - The behavior of the `dismiss` endpoint intentionally differs from the draft: instead of removing the job when its state is `successful`, `failed` or `dismissed`, nothing is changed.
 *       </code>
 *     <p>As this API has not been thoroughly tested yet, there is a chance that it may include
 *     other limitations and/or undesired behavior.
 * @limitationsDe Diese Implementierung deckt nicht alle Details ab und hat einige Einschränkungen:
 *     <p><code>
 *  - Alle Eingaben müssen direkt angegeben werden. Referenzen werden nicht unterstützt.
 *  - Eingaben und Ausgaben können nicht mehrere Schemata haben.
 *  - Von den [JSON Schema Validation Formaten](https://json-schema.org/draft/2020-12/json-schema-validation#name-defined-formats) wird nur `date-time` unterstützt.
 *  - Von den zusätzlichen [OGC PROCESS DESCRIPTION Formaten](https://docs.ogc.org/DRAFTS/18-062r3.html#sc-input-schema) wird nur `ogc-bbox` unterstützt.
 *  - `mediatype`, `encoding` und `schema` werden bei der Ausgabeauswahl nicht berücksichtigt.
 *  - Binärwerte werden nicht unterstützt.
 *  - `null` wird als Eingabewert nicht unterstützt.
 *  - [Anforderung 50](https://docs.ogc.org/DRAFTS/18-062r3.html#_53418543-8dc0-41f2-28df-366acda4d923) ("0-th" result) wird nicht unterstützt.
 *  - Eingabebeschreibungen unterstützen keine [Data classes](https://docs.ogc.org/DRAFTS/18-062r3.html#sc_data_classes), [Data access APIs](https://docs.ogc.org/DRAFTS/18-062r3.html#sc_data_access_APIs) oder [Execution unit requirements](https://docs.ogc.org/DRAFTS/18-062r3.html#sc-execution-unit-requirements).
 *  - Ausgabebeschreibungen unterstützen keine [Data classes](https://docs.ogc.org/DRAFTS/18-062r3.html#_e1f23667-1a0e-bd37-a05b-c9724b59cb48) oder [Data access APIs](https://docs.ogc.org/DRAFTS/18-062r3.html#_e6b07e9e-1559-78f6-c59c-7d7ca363fafb).
 *  - Das Verhalten des `dismiss`-Endpunkts weicht absichtlich vom Entwurf ab: Anstatt den Job zu entfernen, wenn sein Status `successful`, `failed` oder `dismissed` ist, wird nichts geändert.
 *       </code>
 *     <p>Da diese API noch nicht gründlich getestet wurde, können weitere Einschränkungen und/oder
 *     unerwünschtes Verhalten auftreten.
 * @conformanceEn The building block is based on the specifications of the conformance classes
 *     "Core", "OGC Process Description", "JSON", "HTML", "OpenAPI Specification 3.0", "Callback"
 *     and "Dismiss" from the [Draft OGC API - Processes - Part 1:
 *     Core](https://docs.ogc.org/DRAFTS/18-062r3.html). The implementation will change as the draft
 *     evolves during the standardization process.
 * @conformanceDe Der Baustein basiert auf den Vorgaben der Konformitätsklassen "Core", "OGC Process
 *     Description", "JSON", "HTML", "OpenAPI Specification 3.0", "Callback" und "Dismiss" aus dem
 *     [Entwurf von OGC API - Processes - Part 1: Core](https://docs.ogc.org/DRAFTS/18-062r3.html).
 *     Die Implementierung wird sich im Zuge der weiteren Standardisierung der Spezifikation noch
 *     ändern.
 * @ref:cfg {@link de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.processes.domain.ImmutableProcessesCoreConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.processes.infra.EndpointProcessList}, {@link
 *     de.ii.ogcapi.processes.infra.EndpointProcess}, {@link
 *     de.ii.ogcapi.processes.infra.EndpointExecute}, {@link
 *     de.ii.ogcapi.processes.infra.EndpointJob}, {@link
 *     de.ii.ogcapi.processes.infra.EndpointDismiss}, {@link
 *     de.ii.ogcapi.processes.infra.EndpointResults}, {@link
 *     de.ii.ogcapi.processes.infra.EndpointResultsSpecific}, {@link
 *     de.ii.ogcapi.processes.infra.EndpointResultsSpecificN}
 * @ref:pathParameters {@link de.ii.ogcapi.processes.app.parameter.PathParameterProcessId}, {@link
 *     de.ii.ogcapi.processes.app.parameter.PathParameterJobId}, {@link
 *     de.ii.ogcapi.processes.app.parameter.PathParameterOutputId}, {@link
 *     de.ii.ogcapi.processes.app.parameter.PathParameterN}
 * @ref:queryParameters {@link de.ii.ogcapi.processes.app.parameter.QueryParameterLimitProcessList},
 *     {@link de.ii.ogcapi.processes.app.parameter.QueryParameterOffsetProcessList}, {@link
 *     de.ii.ogcapi.processes.app.parameter.QueryParameterFProcess}, {@link
 *     de.ii.ogcapi.processes.app.parameter.QueryParameterFProcessList}, {@link
 *     de.ii.ogcapi.processes.app.parameter.QueryParameterFStatusInfo}
 */
@Singleton
@AutoBind
public class ProcessesCoreBuildingBlock implements ApiBuildingBlock, ConformanceClass {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/18-062r3.html", "OGC API - Processes - Part 1: Core"));

  @Inject
  public ProcessesCoreBuildingBlock() {}

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(
        "https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/core",
        "https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/oas30",
        "https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/ogc-process-description");
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProcessesCoreConfiguration.Builder().enabled(false).build();
  }
}
