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
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
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
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class TransactionJobProcessor extends JobProcessorSimple<TransactionJob> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionJobProcessor.class);

  private static final String RESULT_SUFFIX = ".result.json";

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

  /**
   * Applies the transaction document at {@code documentPath}. If {@code documentPath} is a folder,
   * all documents in it are applied in the order of their names, each as a transaction of its own;
   * the first rejected document stops the job. The documents of a folder are the files with the
   * extensions of the media type ({@code .xml} or {@code .json}, optionally compressed with gzip,
   * {@code .xml.gz} or {@code .json.gz}). An empty folder has nothing to apply.
   *
   * <p>A document compressed with gzip is recognized by its content and decompressed.
   */
  @Override
  public JobResult execute(
      PartialJob partialJob, Job job, TransactionJob inputs, JobProcessing jobs) {
    List<String> validationErrors = validate(inputs);

    if (!validationErrors.isEmpty()) {
      return jobs.failure(validationErrors);
    }

    try {
      OgcApi api = getOgcApi(inputs.getApiId()).get();
      Path documentPath = Path.of(inputs.getDocumentPath());

      if (documentStore.size(documentPath) >= 0) {
        return executeDocument(partialJob, job, inputs, jobs, api, documentPath);
      }

      Optional<List<Path>> documents = folderDocuments(documentPath, inputs.getMediaType());

      if (documents.isEmpty()) {
        return jobs.failure(
            String.format("Transaction document not found: %s", inputs.getDocumentPath()));
      }

      return executeFolder(partialJob, job, inputs, jobs, api, documentPath, documents.get());
    } catch (IOException e) {
      if (LOGGER.isDebugEnabled()) {
        LogContext.errorAsDebug(LOGGER, e, "Error checking transaction document");
      }
      return jobs.failure(String.format("Error checking transaction document: %s", e.getMessage()));
    }
  }

  /**
   * One document. With {@code resultAsFile}, the response document is written to {@code
   * <api>/result_<job>.json}, also if the transaction is rejected, and its path is reported as the
   * job output {@code resultPath}.
   */
  private JobResult executeDocument(
      PartialJob partialJob,
      Job job,
      TransactionJob inputs,
      JobProcessing jobs,
      OgcApi api,
      Path documentPath)
      throws IOException {
    Applied applied = apply(api, inputs, documentPath);

    String resultPath = null;
    if (inputs.getResultAsFile()) {
      if (applied.body() != null) {
        resultPath = String.format("%s/result_%s.json", api.getId(), job.id());
        put(Path.of(resultPath), applied.body());
        jobs.outputs(job.id(), Map.of("resultPath", resultPath));
      }
    } else if (!applied.rejected() && applied.body() != null) {
      jobs.outputs(job.id(), Jobs.DEFAULT_MAPPER.readValue(applied.body(), Jobs.MAP_TYPE));
    }

    if (applied.rejected()) {
      return jobs.failure(
          resultPath != null
              ? String.format(
                  "Transaction failed with status %d, see %s", applied.status(), resultPath)
              : String.format(
                  "Transaction failed with status %d: %s", applied.status(), applied.body()));
    }

    jobs.update(partialJob.id(), 1);
    return jobs.success();
  }

  /**
   * The documents of a folder, in the order of their names. The response document of each applied
   * document is written to the result folder of the job, named after the document ({@code
   * 001.xml.gz} → {@code <api>/result_<job>/001.result.json}), also if it is rejected; the paths
   * are reported as the job output {@code resultPaths}.
   */
  private JobResult executeFolder(
      PartialJob partialJob,
      Job job,
      TransactionJob inputs,
      JobProcessing jobs,
      OgcApi api,
      Path folder,
      List<Path> documents)
      throws IOException {
    List<String> resultPaths = new ArrayList<>();

    for (Path document : documents) {
      Path documentPath = folder.resolve(document);
      Applied applied = apply(api, inputs, documentPath);

      Path resultPath = resultPath(api, job, document);
      if (applied.body() != null) {
        put(resultPath, applied.body());
        resultPaths.add(resultPath.toString());
      }

      if (applied.rejected()) {
        jobs.outputs(job.id(), Map.of("resultPaths", resultPaths));
        return jobs.failure(
            String.format(
                "Transaction %s failed with status %d, see %s",
                documentPath, applied.status(), resultPath));
      }
    }

    jobs.outputs(job.id(), Map.of("resultPaths", resultPaths));
    jobs.update(partialJob.id(), 1);
    return jobs.success();
  }

  /** The outcome of one transaction: the HTTP status and the response document, if any. */
  private record Applied(int status, String body) {
    boolean rejected() {
      return status >= 400;
    }
  }

  private Applied apply(OgcApi api, TransactionJob inputs, Path documentPath) throws IOException {
    Optional<InputStream> content = documentStore.content(documentPath);

    if (content.isEmpty()) {
      throw new IOException("Transaction document not found: " + documentPath);
    }

    try (InputStream document = decompressIfGzip(content.get())) {
      QueryInputTransaction queryInput =
          transactionInputs.createQueryInput(
              api,
              inputs.getMediaType(),
              inputs.getCrs(),
              inputs.getMutationDatetime(),
              inputs.getHandlingPrefer(),
              inputs.getReturnPrefer(),
              document);

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
        Object entity = response.getEntity();
        return new Applied(response.getStatus(), entity == null ? null : entity.toString());
      }
    }
  }

  private static InputStream decompressIfGzip(InputStream content) throws IOException {
    BufferedInputStream buffered = new BufferedInputStream(content, 1 << 16);
    buffered.mark(2);
    int b1 = buffered.read();
    int b2 = buffered.read();
    buffered.reset();
    return b1 == 0x1f && b2 == 0x8b ? new GZIPInputStream(buffered, 1 << 16) : buffered;
  }

  /**
   * The documents in a folder, sorted by name; empty if there is no such folder. A folder in an
   * object store exists only through the objects below it, so any file in it counts.
   */
  private Optional<List<Path>> folderDocuments(Path folder, String mediaType) throws IOException {
    List<String> extensions =
        mediaType.toLowerCase(Locale.ROOT).contains("json")
            ? List.of(".json", ".json.gz")
            : List.of(".xml", ".xml.gz");

    List<Path> files;
    try (Stream<Path> entries =
        documentStore.walk(folder, 1, (path, attributes) -> attributes.isValue())) {
      files =
          entries
              .filter(path -> path.getNameCount() == 1 && !path.toString().isEmpty())
              .filter(path -> !path.startsWith(".."))
              .toList();
    }

    if (files.isEmpty() && !documentStore.has(folder)) {
      return Optional.empty();
    }

    return Optional.of(
        files.stream()
            .filter(
                path -> {
                  String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                  return !name.startsWith(".") && extensions.stream().anyMatch(name::endsWith);
                })
            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
            .toList());
  }

  /**
   * {@code 001.xml.gz} → {@code <api>/result_<job>/001.result.json}: like the result file of a
   * single document, the results of a folder can be found by the id of the job.
   */
  private static Path resultPath(OgcApi api, Job job, Path document) {
    String name = document.getFileName().toString();
    if (name.toLowerCase(Locale.ROOT).endsWith(".gz")) {
      name = name.substring(0, name.length() - 3);
    }
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    return Path.of(api.getId(), String.format("result_%s", job.id()), name + RESULT_SUFFIX);
  }

  private void put(Path path, String content) throws IOException {
    documentStore.put(path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
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

    Optional<OgcApi> api = getOgcApi(inputs.getApiId());

    if (api.isEmpty()) {
      errors.add(String.format("API with id '%s' not found", inputs.getApiId()));
    }

    Optional<TransactionsConfiguration> cfg =
        api.flatMap(
            a ->
                a.getData()
                    .getExtension(TransactionsConfiguration.class)
                    .filter(TransactionsConfiguration::isEnabled));

    if (cfg.isEmpty()) {
      errors.add(
          String.format(
              "Transactions building block is not enabled for API '%s'", inputs.getApiId()));
    }

    if (Strings.isNullOrEmpty(inputs.getMediaType())) {
      errors.add("Document media type must not be null or empty");
    }

    if (Strings.isNullOrEmpty(inputs.getDocumentPath())) {
      errors.add("Document path must not be null or empty");
    }

    if (!Strings.isNullOrEmpty(inputs.getDocumentPath())
        && Path.of(inputs.getDocumentPath()).isAbsolute()) {
      errors.add(
          String.format(
              "Transaction document path must be relative: %s", inputs.getDocumentPath()));
    }

    return errors;
  }
}
