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

/**
 * @buildingBlock PROCESSES_CORE
 * @examplesEn Example config for an API:
 *     <p><code>
 *   ```yaml
 *  - buildingBlock: PROCESSES_CORE
 *     enabled: true
 *     defaultPageSize: 10
 *     minimumPageSize: 5
 *     maximumPageSize: 100
 *     callbackRetries: 2
 *     executionMode: BOTH
 *   ```
 * </code>
 *     <p>Example for the process description of a simple `Echo`-Process:<code>
 *   ```yaml
 *   id: "EchoProcess"
 * title: "Echo Process"
 * description: "This process simply echoes its input as an output."
 * version: "1.0.0"
 * jobControlOptions: [ SYNC_EXECUTE, ASYNC_EXECUTE ]
 * outputs:
 *   inputString:
 *     title: "Input String"
 *     description: "The echoed single string value."
 *     valuePassing: [ BY_VALUE ]
 *     schema:
 *       type: string
 *     maxOccurs: 1
 *     minOccurs: 0
 * inputs:
 *   inputString:
 *     title: "Input String"
 *     description: "A single string value to echo."
 *     valuePassing: [ BY_VALUE ]
 *     schema:
 *       type: string
 *     maxOccurs: 1
 *     minOccurs: 0
 *   ```
 * </code>
 *     <p>If the process is meant to be global, this must be stored in `values/processes`. If the
 *     process is for a specific API `foo`, it must be in `values/processes/foo`.
 *     <p>For more information on how to describe a process, see [OGC PROCESS
 *     DESCRIPTION](https://docs.ogc.org/DRAFTS/18-062r3.html#ogc_process_description).
 * @exampleDe Beispielkonfiguration für eine API:
 *     <p><code>
 *   ```yaml
 *  - buildingBlock: PROCESSES_CORE
 *     enabled: true
 *     defaultPageSize: 10
 *     minimumPageSize: 5
 *     maximumPageSize: 100
 *     callbackRetries: 2
 *     executionMode: BOTH
 *   ```
 * </code>
 *     <p>Beispiel für die Prozessbeschreibung eines einfachen `Echo`-Prozesses:<code>
 *   ```yaml
 *   id: "EchoProcess"
 * title: "Echo Process"
 * description: "This process simply echoes its input as an output."
 * version: "1.0.0"
 * jobControlOptions: [ SYNC_EXECUTE, ASYNC_EXECUTE ]
 * outputs:
 *   inputString:
 *     title: "Input String"
 *     description: "The echoed single string value."
 *     valuePassing: [ BY_VALUE ]
 *     schema:
 *       type: string
 *     maxOccurs: 1
 *     minOccurs: 0
 * inputs:
 *   inputString:
 *     title: "Input String"
 *     description: "A single string value to echo."
 *     valuePassing: [ BY_VALUE ]
 *     schema:
 *       type: string
 *     maxOccurs: 1
 *     minOccurs: 0
 *   ```
 * </code>
 *     <p>Wenn der Prozess global sein soll, muss die Beschreibung in `values/processes` gespeichert
 *     werden. Falls der Prozess für eine bestimmte API `foo` gelten soll, muss dieser in
 *     `values/processes/foo` liegen.
 *     <p>Weitere Informationen zur Beschreibung eines Prozesses finden Sie unter [OGC PROCESS
 *     DESCRIPTION](https://docs.ogc.org/DRAFTS/18-062r3.html#ogc_process_description).
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PROCESSES_CORE")
@JsonDeserialize(builder = ImmutableProcessesCoreConfiguration.Builder.class)
public interface ProcessesCoreConfiguration extends ExtensionConfiguration {

  enum ExecutionMode {
    SYNC,
    ASYNC,
    BOTH
  }

  int DEFAULT_PAGE_SIZE = 10;
  int MINIMUM_PAGE_SIZE = 1;
  int MAXIMUM_PAGE_SIZE = 10_000;

  /**
   * @default false
   * @since 4.9
   */
  @Nullable
  @Override
  Boolean getEnabled();

  /**
   * @langEn Sets the supported execution modes. If set to `SYNC`, only synchronous processes will
   *     be supported and they will all be executed synchronously. Setting it to `ASYNC` has the
   *     same effect for asynchronous processes and execution. To support both modes, set it to
   *     `BOTH`.
   * @langDe Legt die unterstützten Ausführungsmodi fest. Bei `SYNC` werden nur synchrone Prozesse
   *     unterstützt und alle synchron ausgeführt. Bei `ASYNC` gilt dies entsprechend für asynchrone
   *     Prozesse und Ausführung. Um beide Modi zu unterstützen, setzen Sie den Wert auf `BOTH`.
   * @default SYNC
   * @since 4.9
   */
  @Value.Default
  default ExecutionMode getExecutionMode() {
    return ExecutionMode.SYNC;
  }

  /**
   * @langEn Sets the default value for parameter `limit`.
   * @langDe Setzt den Defaultwert für den Parameter `limit`.
   * @default 10
   * @since 4.9
   */
  @Value.Default
  default Integer getDefaultPageSize() {
    return DEFAULT_PAGE_SIZE;
  }

  /**
   * @langEn Sets the minimum value for parameter `limit`.
   * @langDe Setzt den Minimalwert für den Parameter `limit`.
   * @default 1
   * @since 4.9
   */
  @Value.Default
  default Integer getMinimumPageSize() {
    return MINIMUM_PAGE_SIZE;
  }

  /**
   * @langEn Sets the maximum value for parameter `limit`.
   * @langDe Setzt den Maximalwert für den Parameter `limit`.
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

  @Override
  default Builder getBuilder() {
    return new ImmutableProcessesCoreConfiguration.Builder();
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}
}
