/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Strings;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.transactions.app.CommandHandlerTransactions.QueryInputTransaction;
import de.ii.ogcapi.transactions.domain.TransactionJob;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobResult;
import de.ii.xtralink.jobs.PartialJob;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.xtralink.domain.JobProcessing;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import de.ii.xtraplatform.xtralink.domain.JobProcessorSimple;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class TransactionJobProcessor extends JobProcessorSimple<TransactionJob> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionJobProcessor.class);

  private final AppContext appContext;
  private final EntityRegistry entityRegistry;
  private final ResourceStore documentStore;
  private final TransactionInputs transactionInputs;
  private final CommandHandlerTransactions commandHandler;

  @Inject
  TransactionJobProcessor(
      AppContext appContext,
      EntityRegistry entityRegistry,
      ResourceStore resourceStore,
      TransactionInputs transactionInputs,
      CommandHandlerTransactions commandHandler) {
    this.appContext = appContext;
    this.entityRegistry = entityRegistry;
    this.documentStore = resourceStore.with(Jobs.RESOURCE_TYPE, TransactionJob.KIND);
    this.transactionInputs = transactionInputs;
    this.commandHandler = commandHandler;
  }

  @Override
  public String getKind() {
    return TransactionJob.KIND;
  }

  @Override
  public JobResult setup(
      PartialJob partialJob, Job job, TransactionJob inputs, JobProcessing jobs) {
    return jobs.success();
  }

  @Override
  public JobResult execute(
      PartialJob partialJob, Job job, TransactionJob inputs, JobProcessing jobs) {
    List<String> validationErrors = validate(inputs);

    if (!validationErrors.isEmpty()) {
      return jobs.failure(validationErrors);
    }

    try {
      Optional<InputStream> document = documentStore.content(Path.of(inputs.getDocumentPath()));

      if (document.isEmpty()) {
        return jobs.failure(
            String.format("Transaction document not found: %s", inputs.getDocumentPath()));
      }

      OgcApi api = getOgcApi(inputs.getApiId()).get();

      QueryInputTransaction queryInput =
          transactionInputs.createQueryInput(
              api,
              inputs.getMediaType(),
              inputs.getCrs(),
              inputs.getMutationDatetime(),
              inputs.getHandlingPrefer(),
              inputs.getReturnPrefer(),
              document.get());

      ApiRequestContext requestContext =
          new ImmutableStaticRequestContext.Builder()
              .webContext(appContext)
              .api(api)
              .requestUri(api.getUri())
              .mediaType(
                  new ImmutableApiMediaType.Builder().type(queryInput.getContentType()).build())
              .alternateMediaTypes(Set.of())
              .queryParameterSet(QueryParameterSet.of())
              .build();

      try (Response response = commandHandler.processTransaction(queryInput, requestContext)) {
        if (response.getStatus() >= 400) {
          return jobs.failure(
              String.format(
                  "Transaction failed with status %d: %s",
                  response.getStatus(), response.readEntity(String.class)));
        }

        if (inputs.getResultAsFile()) {
          String resultPath = String.format("%s/result_%s.json", api.getId(), job.id());
          documentStore.put(
              Path.of(resultPath),
              new ByteArrayInputStream(
                  ((String) response.getEntity()).getBytes(StandardCharsets.UTF_8)));
          jobs.outputs(job.id(), Map.of("resultPath", resultPath));
        } else {
          jobs.outputs(
              job.id(),
              Jobs.DEFAULT_MAPPER.readValue((String) response.getEntity(), Jobs.MAP_TYPE));
        }
      }
      jobs.update(partialJob.id(), 1);

    } catch (IOException e) {
      if (LOGGER.isDebugEnabled()) {
        LogContext.errorAsDebug(LOGGER, e, "Error checking transaction document");
      }
      return jobs.failure(String.format("Error checking transaction document: %s", e.getMessage()));
    }

    return jobs.success();
  }

  @Override
  public JobResult cleanup(
      PartialJob partialJob, Job job, TransactionJob inputs, JobProcessing jobs) {
    return jobs.success();
  }

  @Override
  public Class<TransactionJob> getInputsClass() {
    return TransactionJob.class;
  }

  private Optional<OgcApi> getOgcApi(String apiId) {
    return entityRegistry.getEntity(OgcApi.class, apiId);
  }

  private List<String> validate(TransactionJob inputs) {
    List<String> errors = new ArrayList<>();

    if (Strings.isNullOrEmpty(inputs.getApiId())) {
      errors.add("API id must not be null or empty");
    }

    if (getOgcApi(inputs.getApiId()).isEmpty()) {
      errors.add(String.format("API with id '%s' not found", inputs.getApiId()));
    }

    if (Strings.isNullOrEmpty(inputs.getMediaType())) {
      errors.add("Document media type must not be null or empty");
    }

    if (Strings.isNullOrEmpty(inputs.getDocumentPath())) {
      errors.add("Document path must not be null or empty");
    }

    if (Path.of(inputs.getDocumentPath()).isAbsolute()) {
      errors.add(
          String.format(
              "Transaction document path must be relative: %s", inputs.getDocumentPath()));
    }

    return errors;
  }
}
