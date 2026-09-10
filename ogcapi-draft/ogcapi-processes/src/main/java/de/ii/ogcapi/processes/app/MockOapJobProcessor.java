/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.processes.domain.OapJob;
import de.ii.ogcapi.processes.domain.OapJob.OapJobContext;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobResult;
import de.ii.xtralink.jobs.PartialJob;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.xtralink.domain.JobProcessing;
import de.ii.xtraplatform.xtralink.domain.JobProcessor;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class MockOapJobProcessor implements JobProcessor<OapJob, OapJobContext> {

  private final Map<String, Function<Map<String, Object>, Map<String, Object>>> processes;

  @Inject
  MockOapJobProcessor(AppContext appContext) {
    this.processes =
        appContext.isDevEnv()
            ? Map.of(
                "EchoProcess",
                this::echoProcess,
                "AnswerProcess",
                this::answerProcess,
                "AdditionProcess",
                this::additionProcess,
                "CountingProcess",
                this::countingProcess)
            : Map.of();
  }

  @Override
  public Set<String> getKinds() {
    return processes.keySet().stream().map(OapJob::kind).collect(Collectors.toSet());
  }

  @Override
  public int getPriority() {
    return 1000;
  }

  @Override
  public JobResult process(PartialJob partialJob, Job job, JobProcessing jobs) throws Exception {
    jobs.init(job.id(), 1, null);

    OapJob oapJob = getInputs(job, jobs);

    Map<String, Object> outputs =
        switch (partialJob.kind()) {
          case "oap:EchoProcess" -> processes.get("EchoProcess").apply(oapJob.getInputs());
          case "oap:AnswerProcess" -> processes.get("AnswerProcess").apply(oapJob.getInputs());
          case "oap:AdditionProcess" -> processes.get("AdditionProcess").apply(oapJob.getInputs());
          case "oap:CountingProcess" -> processes.get("CountingProcess").apply(oapJob.getInputs());
          default -> throw new IllegalArgumentException("Unknown job kind: " + partialJob.kind());
        };

    jobs.outputs(job.id(), outputs);

    jobs.update(partialJob.id(), 1);

    return jobs.success();
  }

  @Override
  public Class<OapJob> getInputsClass() {
    return OapJob.class;
  }

  @Override
  public Class<OapJobContext> getPartialContextClass() {
    return OapJobContext.class;
  }

  private Map<String, Object> echoProcess(Map<String, Object> inputs) {
    return inputs;
  }

  private Map<String, Object> countingProcess(Map<String, Object> inputs) {
    int n = (Integer) inputs.get("N");

    List<Integer> arrayN = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      arrayN.add(i);
    }

    return Map.of("numbers", arrayN);
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private Map<String, Object> answerProcess(Map<String, Object> inputs) {
    return Map.of("answer", 42);
  }

  private Map<String, Object> additionProcess(Map<String, Object> inputs) {
    int firstAddend = (Integer) inputs.get("firstAddend");
    int secondAddend = (Integer) inputs.get("secondAddend");
    return Map.of("sum", firstAddend + secondAddend);
  }
}
