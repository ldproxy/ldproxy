/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import java.util.Map;

/**
 * Processes that are not process descriptions in {@code values/processes}, but derived from other
 * values, e.g. the workflows of an Arazzo document. They are processes like all others: the
 * execution, the jobs and the endpoints are those of the building block PROCESSES_CORE. A process
 * description in {@code values/processes} with the same id takes precedence.
 */
@AutoMultiBind
// a service with implementations that are bound by the platform, not a target of lambdas
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ProcessContribution {

  /**
   * @param apiId the id of the API
   * @return the processes of the API by id; the values can change at any time, e.g. after a reload
   *     of the values
   */
  Map<String, ProcessData> getProcesses(String apiId);
}
