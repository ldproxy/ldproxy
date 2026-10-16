/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Strings;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputQuery;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchJob;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.Query;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.QueryInputQuery;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class SearchJobProcessor extends JobProcessorSimple<SearchJob> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchJobProcessor.class);

  private final AppContext appContext;
  private final EntityRegistry entityRegistry;
  private final ResourceStore resultStore;
  private final FeaturesCoreProviders providers;
  private final SearchQueriesHandler queryHandler;

  @Inject
  SearchJobProcessor(
      AppContext appContext,
      EntityRegistry entityRegistry,
      ResourceStore resourceStore,
      FeaturesCoreProviders providers,
      SearchQueriesHandler queryHandler) {
    this.appContext = appContext;
    this.entityRegistry = entityRegistry;
    this.resultStore = resourceStore.with(Jobs.RESOURCE_TYPE, SearchJob.KIND);
    this.providers = providers;
    this.queryHandler = queryHandler;
  }

  @Override
  public String getKind() {
    return SearchJob.KIND;
  }

  @Override
  public Class<SearchJob> getInputsClass() {
    return SearchJob.class;
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
      List<String> validationErrors = validate(inputs);

      if (!validationErrors.isEmpty()) {
        return jobs.failure(validationErrors);
      }

      OgcApi api = getOgcApi(inputs.getApiId()).get();
      FeaturesCoreConfiguration coreConfiguration =
          api.getData().getExtension(FeaturesCoreConfiguration.class).orElseThrow();

      QueryExpression query =
          QueryExpression.of(
              new ByteArrayInputStream(
                  inputs.getQueryExpression().getBytes(StandardCharsets.UTF_8)));

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
              .requestUri(URI.create(inputs.getRequestUri()))
              .mediaType(
                  new ImmutableApiMediaType.Builder()
                      .type(MediaType.valueOf(inputs.getMediaType()))
                      .label(inputs.getMediaTypeLabel())
                      .parameter(inputs.getMediaTypeParameter())
                      .build())
              .alternateMediaTypes(Set.of())
              .language(Optional.ofNullable(inputs.getLanguage()).map(Locale::forLanguageTag))
              .queryParameterSet(QueryParameterSet.of())
              .build();

      try (Response response = queryHandler.handle(Query.QUERY, queryInput, requestContext)) {
        if (response.getStatus() >= 400) {
          return jobs.failure(String.format("Query failed with status %d", response.getStatus()));
        }

        String resultPath =
            String.format("%s/result_%s.%s", api.getId(), job.id(), inputs.getMediaTypeParameter());
        storeResult(response, resultPath);
        jobs.outputs(job.id(), Map.of("resultPath", resultPath));
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
  private void storeResult(Response response, String resultPath) throws IOException {
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
        resultStore.put(Path.of(resultPath), in);
      }
    } finally {
      Files.deleteIfExists(tmpFile);
    }
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

    if (Strings.isNullOrEmpty(inputs.getQueryExpression())) {
      errors.add("Query expression must not be null or empty");
    }

    if (Strings.isNullOrEmpty(inputs.getMediaType())) {
      errors.add("Result media type must not be null or empty");
    }

    return errors;
  }
}
