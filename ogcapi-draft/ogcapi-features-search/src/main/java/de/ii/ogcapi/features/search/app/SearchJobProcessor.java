/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Strings;
import de.ii.ogcapi.common.domain.async.JobFailure;
import de.ii.ogcapi.common.domain.async.JobResultFile;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputQuery;
import de.ii.ogcapi.features.search.domain.ImmutableSearchJob;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchJob;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.Query;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.QueryInputQuery;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobResult;
import de.ii.xtralink.jobs.PartialJob;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.xtralink.domain.JobProcessing;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import de.ii.xtraplatform.xtralink.domain.JobProcessorSimple;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class SearchJobProcessor extends JobProcessorSimple<SearchJob> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchJobProcessor.class);

  /**
   * A search can also run as a step of a job of another kind, e.g. a process with the step {@code
   * feature-query:execute}. An earlier step then gives the call of the stored query as the job
   * output {@code queryPath}: the path of a JSON document in the store of the search jobs, whose
   * member {@code query} has {@code api}, {@code queryId} and {@code parameters}.
   */
  static final String QUERY_PATH = "queryPath";

  /** The job outputs of a search that runs as a step of a job of another kind. */
  static final String STEP_RESULT_PATH = "searchResultPath";

  static final String STEP_RESULT_MEDIA_TYPE = "searchResultMediaType";

  // the inputs of a job of another kind are not those of a search; the query of a step is read in
  // execute, where a missing or invalid query fails the job instead of the processor
  private static final SearchJob STEP_INPUTS = new ImmutableSearchJob.Builder().apiId("").build();

  private static final ObjectMapper JSON = new ObjectMapper();

  private final AppContext appContext;
  private final EntityRegistry entityRegistry;
  private final FeaturesCoreProviders providers;
  private final SearchQueriesHandler queryHandler;
  private final StoredQueryResolver storedQueryResolver;

  @Inject
  SearchJobProcessor(
      AppContext appContext,
      EntityRegistry entityRegistry,
      ResourceStore resourceStore,
      FeaturesCoreProviders providers,
      SearchQueriesHandler queryHandler,
      ExtensionRegistry extensionRegistry,
      StoredQueryRepository repository,
      SchemaValidator schemaValidator,
      Cql cql) {
    super(SearchJob.KIND, resourceStore);

    this.appContext = appContext;
    this.entityRegistry = entityRegistry;
    this.providers = providers;
    this.queryHandler = queryHandler;
    this.storedQueryResolver =
        new StoredQueryResolver(extensionRegistry, repository, schemaValidator, cql);
  }

  @Override
  public Class<SearchJob> getInputsClass() {
    return SearchJob.class;
  }

  @Override
  public SearchJob getInputs(Job job, JobProcessing jobs) {
    return isStep(job) ? STEP_INPUTS : super.getInputs(job, jobs);
  }

  @Override
  public JobResult setup(PartialJob partialJob, Job job, SearchJob inputs, JobProcessing jobs) {
    return jobs.success();
  }

  @Override
  public JobResult execute(PartialJob partialJob, Job job, SearchJob inputs, JobProcessing jobs) {
    // fine-grained progress goes into the job details only; the counters stay within the 1-unit
    // model so the backend's failure handling finalizes the job on every outcome
    SearchJobHook jobHook = new SearchJobHook(jobs, job.id());

    try {
      if (isStep(job)) {
        try {
          inputs = readStepQuery(job);
        } catch (IllegalArgumentException e) {
          return jobs.failure(e.getMessage());
        }
      }

      List<String> validationErrors = validate(inputs);

      if (!validationErrors.isEmpty()) {
        return jobs.failure(validationErrors);
      }

      OgcApi api = getOgcApi(inputs.getApiId()).get();
      FeaturesCoreConfiguration coreConfiguration =
          api.getData().getExtension(FeaturesCoreConfiguration.class).orElseThrow();

      QueryExpression query;
      ApiMediaType mediaType;
      URI requestUri;
      QueryParameterSet queryParameterSet;
      if (Objects.nonNull(inputs.getQueryId())) {
        StoredQueryResolver.Resolved resolved;
        try {
          resolved =
              storedQueryResolver.resolveForJob(api, inputs.getQueryId(), inputs.getParameters());
        } catch (IllegalArgumentException | WebApplicationException e) {
          return jobs.failure(e.getMessage());
        }
        query = resolved.getQuery();
        mediaType = resolved.getMediaType();
        requestUri = resolved.getRequestUri();
        queryParameterSet = resolved.getQueryParameterSet();
      } else {
        query =
            QueryExpression.of(
                new ByteArrayInputStream(
                    inputs.getQueryExpression().getBytes(StandardCharsets.UTF_8)));
        mediaType =
            new ImmutableApiMediaType.Builder()
                .type(MediaType.valueOf(inputs.getMediaType()))
                .label(inputs.getMediaTypeLabel())
                .parameter(inputs.getMediaTypeParameter())
                .build();
        requestUri = URI.create(inputs.getRequestUri());
        queryParameterSet = QueryParameterSet.of();
      }

      QueryInputQuery queryInput =
          new ImmutableQueryInputQuery.Builder()
              .query(query)
              .featureProvider(providers.getFeatureProviderOrThrow(api.getData()))
              .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
              .minimumPageSize(Optional.ofNullable(coreConfiguration.getMinimumPageSize()))
              .defaultPageSize(Optional.ofNullable(coreConfiguration.getDefaultPageSize()))
              .maximumPageSize(Optional.ofNullable(coreConfiguration.getMaximumPageSize()))
              .allLinksAreLocal(
                  api.getData()
                      .getExtension(SearchConfiguration.class)
                      .map(SearchConfiguration::getAllLinksAreLocal)
                      .orElse(false))
              .isStoredQuery(inputs.getIsStoredQuery())
              .jobHook(jobHook)
              .build();

      ApiRequestContext requestContext =
          new ImmutableStaticRequestContext.Builder()
              .webContext(appContext)
              .api(api)
              .requestUri(requestUri)
              .mediaType(mediaType)
              .alternateMediaTypes(Set.of())
              .language(Optional.ofNullable(inputs.getLanguage()).map(Locale::forLanguageTag))
              .queryParameterSet(queryParameterSet)
              .build();

      try (Response response = queryHandler.handle(Query.QUERY, queryInput, requestContext)) {
        if (response.getStatus() >= 400) {
          // the status of the query is the reason for the failure of the job
          JobFailure failure =
              JobFailure.of(
                  response.getStatus(),
                  String.format("Query failed with status %d", response.getStatus()));
          jobs.outputs(job.id(), isStep(job) ? failure.addTo(outputsOf(job)) : failure.asOutputs());
          return jobs.failure(failure.getDetail());
        }

        String resultPath =
            String.format("%s/result_%s.%s", api.getId(), job.id(), mediaType.parameter());
        storeResult(job, response, resultPath);

        if (isStep(job)) {
          // the outputs replace those of the job, so the outputs of the earlier steps are kept;
          // the result is not the result of the job, so it is not given as a result file
          Map<String, Object> outputs = new LinkedHashMap<>(outputsOf(job));
          outputs.put(STEP_RESULT_PATH, resultPath);
          outputs.put(STEP_RESULT_MEDIA_TYPE, mediaType.type().toString());
          jobs.outputs(job.id(), outputs);
        } else {
          jobs.outputs(job.id(), JobResultFile.of(resultPath, mediaType.type().toString()));
        }
      }

      // book the execute partial's single unit only on success; on failure the backend tops the
      // partial up itself
      jobs.update(partialJob.id(), 1);
    } catch (IOException e) {
      if (LOGGER.isDebugEnabled()) {
        LogContext.errorAsDebug(LOGGER, e, "Error executing query job");
      }
      return jobs.failure(String.format("Error executing query: %s", e.getMessage()));
    } finally {
      jobHook.finish();
    }

    return jobs.success();
  }

  @Override
  public JobResult cleanup(PartialJob partialJob, Job job, SearchJob inputs, JobProcessing jobs) {
    return jobs.success();
  }

  /**
   * The response entity is a streaming output; it is written to a local temporary file first and
   * then moved into the resources store.
   */
  private void storeResult(Job job, Response response, String resultPath) throws IOException {
    Path tmpFile = Files.createTempFile(appContext.getTmpDir(), "search-result", ".tmp");
    try {
      if (response.getEntity() instanceof StreamingOutput streamingOutput) {
        try (OutputStream out = Files.newOutputStream(tmpFile)) {
          streamingOutput.write(out);
        }
      } else {
        String body = response.getEntity() == null ? "" : String.valueOf(response.getEntity());
        Files.writeString(tmpFile, body, StandardCharsets.UTF_8);
      }
      try (InputStream in = Files.newInputStream(tmpFile)) {
        if (isStep(job)) {
          // the finalize phase of search jobs never runs for a job of another kind; a later step
          // of that job removes the result
          getStore().put(Path.of(resultPath), in);
        } else {
          saveOutputFile(job, Path.of(resultPath), in);
        }
      }
    } finally {
      Files.deleteIfExists(tmpFile);
    }
  }

  private static boolean isStep(Job job) {
    return !SearchJob.KIND.equals(job.kind());
  }

  private static Map<String, Object> outputsOf(Job job) {
    return Objects.requireNonNullElse(job.outputs(), Map.of());
  }

  // xtralink writes the outputs of workflow steps as {"value": ..., "href": ..., "kind": ...}
  private static Object unwrap(Object value) {
    if (value instanceof Map<?, ?> map && map.containsKey("value")) {
      return map.get("value");
    }
    return value;
  }

  private SearchJob readStepQuery(Job job) throws IOException {
    Object value = unwrap(outputsOf(job).get(QUERY_PATH));
    if (!(value instanceof String queryPath) || queryPath.isBlank()) {
      throw new IllegalArgumentException(
          String.format(
              "The job has no output '%s' with the path of the query to execute", QUERY_PATH));
    }

    Path path = Path.of(queryPath).normalize();
    if (path.isAbsolute() || path.startsWith("..")) {
      throw new IllegalArgumentException(
          String.format("The path of the query '%s' is outside of the store", queryPath));
    }

    Optional<InputStream> content = getStore().content(path);
    if (content.isEmpty()) {
      throw new IllegalArgumentException(String.format("The query '%s' does not exist", queryPath));
    }

    JsonNode query;
    try (InputStream in = content.get()) {
      query = JSON.readTree(in).path("query");
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          String.format("The query '%s' is not valid JSON", queryPath));
    }

    ImmutableSearchJob.Builder builder =
        new ImmutableSearchJob.Builder()
            .apiId(query.path("api").asText(""))
            .queryId(query.path("queryId").isTextual() ? query.path("queryId").asText() : null);
    query
        .path("parameters")
        .properties()
        .forEach(
            parameter -> builder.putParameters(parameter.getKey(), parameter.getValue().asText()));

    return builder.build();
  }

  private Optional<OgcApi> getOgcApi(String apiId) {
    return entityRegistry.getEntity(OgcApi.class, apiId);
  }

  private List<String> validate(SearchJob inputs) {
    List<String> errors = new ArrayList<>();

    if (Strings.isNullOrEmpty(inputs.getApiId())) {
      errors.add("API id must not be null or empty");
    }

    if (getOgcApi(inputs.getApiId()).isEmpty()) {
      errors.add(String.format("API with id '%s' not found", inputs.getApiId()));
    }

    boolean hasQueryExpression = !Strings.isNullOrEmpty(inputs.getQueryExpression());
    boolean hasQueryId = !Strings.isNullOrEmpty(inputs.getQueryId());

    if (hasQueryExpression == hasQueryId) {
      errors.add("Exactly one of query expression and stored query id must be given");
    }

    if (hasQueryExpression) {
      if (Strings.isNullOrEmpty(inputs.getRequestUri())) {
        errors.add("Request URI must not be null or empty");
      }
      if (Strings.isNullOrEmpty(inputs.getMediaType())
          || Strings.isNullOrEmpty(inputs.getMediaTypeLabel())
          || Strings.isNullOrEmpty(inputs.getMediaTypeParameter())) {
        errors.add("Result media type must not be null or empty");
      }
    }

    return errors;
  }
}
