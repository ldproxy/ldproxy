/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.xtraplatform.base.domain.LogContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@AutoBind
public class ProcessesExecutorImpl implements ProcessesExecutor {

  Map<String, STATUS_CODE> jobMap = new ConcurrentHashMap<>();

  @Inject
  ProcessesExecutorImpl() {}

  @Override
  public String execute(String processId) {
    return execute(processId, "");
  }

  @Override
  public String execute(String processId, String Input) {
    String jobId = LogContext.generateRandomUuid().toString();
    jobMap.put(jobId, STATUS_CODE.ACCEPTED);
    return jobId;
  }

  @Override
  public STATUS_CODE status(String jobId) {
    return jobMap.get(jobId);
  }

  @Override
  public String result(String jobId) {
    if (jobMap.get(jobId) == STATUS_CODE.SUCCESSFUL) {
      return "Job '" + jobId + "' finished";
    }
    return jobMap.get(jobId).toString();
  }
}
