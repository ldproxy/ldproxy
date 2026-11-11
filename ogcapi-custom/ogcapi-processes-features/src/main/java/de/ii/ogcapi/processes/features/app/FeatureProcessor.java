/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import de.ii.ogcapi.common.domain.async.JobFailure;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.processes.domain.OapJob;
import de.ii.ogcapi.processes.features.domain.ProcessesFeaturesConfiguration;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobResult;
import de.ii.xtralink.jobs.PartialJob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity;
import de.ii.xtraplatform.xtralink.domain.JobProcessing;
import de.ii.xtraplatform.xtralink.domain.JobProcessorSimple;
import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A step of a process, e.g. {@code feature-aggregate:execute}: it reads the inputs of the process
 * and writes its outputs. A failure that the request causes, e.g. an invalid input or a selection
 * with too many features, is a client error of the execution.
 */
abstract class FeatureProcessor extends JobProcessorSimple<OapJob> {

  private final EntityRegistry entityRegistry;

  FeatureProcessor(String kind, ResourceStore resourceStore, EntityRegistry entityRegistry) {
    super(kind, resourceStore);
    this.entityRegistry = entityRegistry;
  }

  /**
   * @return the outputs of the process
   * @throws IllegalArgumentException if the request is invalid
   */
  protected abstract Map<String, Object> process(
      OgcApi api, ProcessesFeaturesConfiguration configuration, Map<String, Object> inputs)
      throws IOException;

  @Override
  public Class<OapJob> getInputsClass() {
    return OapJob.class;
  }

  @Override
  public JobResult setup(PartialJob partialJob, Job job, OapJob inputs, JobProcessing jobs) {
    return jobs.success();
  }

  @Override
  public JobResult execute(PartialJob partialJob, Job job, OapJob inputs, JobProcessing jobs) {
    String apiId = JobContextEntity.from(job.context());
    Optional<OgcApi> api = entityRegistry.getEntity(OgcApi.class, apiId);
    if (api.isEmpty()) {
      return jobs.failure(String.format("API with id '%s' not found", apiId));
    }
    Optional<ProcessesFeaturesConfiguration> configuration =
        api.get()
            .getData()
            .getExtension(ProcessesFeaturesConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled);
    if (configuration.isEmpty()) {
      // a process uses the step, but the building block is not enabled; a configuration error
      return jobs.failure(
          String.format(
              "The building block PROCESSES_FEATURES is not enabled in API '%s'.", apiId));
    }

    Map<String, Object> outputs;
    try {
      outputs = process(api.get(), configuration.get(), inputs.getInputs());
    } catch (IllegalArgumentException e) {
      return clientFailure(job, jobs, 400, e.getMessage());
    } catch (WebApplicationException e) {
      int status = e.getResponse().getStatus();
      if (JobFailure.isClientError(status)) {
        return clientFailure(job, jobs, status, e.getMessage());
      }
      throw e;
    } catch (IOException e) {
      // unexpected; the errors of a job are public, so it is only logged with an error id
      throw new UncheckedIOException(e);
    }

    jobs.outputs(job.id(), outputs);
    jobs.update(partialJob.id(), 1);
    return jobs.success();
  }

  @Override
  public JobResult cleanup(PartialJob partialJob, Job job, OapJob inputs, JobProcessing jobs) {
    return jobs.success();
  }

  protected static int maximumFeatures(ProcessesFeaturesConfiguration configuration) {
    return Objects.requireNonNullElse(
        configuration.getMaximumFeaturesScanned(),
        ProcessesFeaturesBuildingBlock.MAXIMUM_FEATURES_SCANNED);
  }

  protected static IllegalArgumentException tooManyFeatures(int maximum) {
    return new IllegalArgumentException(
        String.format(
            "The selection has more than %d features, narrow it, e.g. with a smaller area or further parameters.",
            maximum));
  }

  protected static Object required(Map<String, Object> inputs, String input) {
    Object value = inputs.get(input);
    if (Objects.isNull(value)) {
      throw new IllegalArgumentException(String.format("The input '%s' is required.", input));
    }
    return value;
  }

  private static JobResult clientFailure(Job job, JobProcessing jobs, int status, String detail) {
    JobFailure failure = JobFailure.of(status, detail);
    jobs.outputs(job.id(), failure.asOutputs());
    return jobs.failure(failure.getDetail());
  }
}
