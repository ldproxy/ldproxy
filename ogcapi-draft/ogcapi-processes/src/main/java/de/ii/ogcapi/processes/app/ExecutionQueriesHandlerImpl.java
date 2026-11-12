/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.common.domain.QueryParameterProfile;
import de.ii.ogcapi.common.domain.async.ImmutableOgcApiJobResults;
import de.ii.ogcapi.common.domain.async.ImmutableOgcApiJobStatusInfo;
import de.ii.ogcapi.common.domain.async.JobResultsFormatExtension;
import de.ii.ogcapi.common.domain.async.JobStatusFormatExtension;
import de.ii.ogcapi.common.domain.async.OgcApiJobResults;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatusInfo;
import de.ii.ogcapi.common.domain.async.StatusInfoLinksGenerator;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.processes.domain.AllowedUris;
import de.ii.ogcapi.processes.domain.ExecutionQueriesHandler;
import de.ii.ogcapi.processes.domain.ExecutionRequestContext;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.model.OgcExecute;
import de.ii.ogcapi.processes.domain.model.OgcProcess;
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ExecutionQueriesHandlerImpl extends AbstractVolatileComposed
    implements ExecutionQueriesHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionQueriesHandlerImpl.class);

  private final ProcessRepository processRepository;
  private final ProcessesExecutor processesExecutor;
  private final Map<ExecutionQueriesHandler.Query, QueryHandler<? extends QueryInput>>
      queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final ObjectMapper mapper;
  private final I18n i18n;

  @Inject
  public ExecutionQueriesHandlerImpl(
      I18n i18n,
      ExtensionRegistry extensionRegistry,
      ProcessRepository processRepository,
      ProcessesExecutor processesExecutor,
      VolatileRegistry volatileRegistry,
      Jackson jackson) {
    super(ExecutionQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.processRepository = processRepository;
    this.processesExecutor = processesExecutor;

    this.mapper = jackson.getDefaultObjectMapper();

    this.queryHandlers =
        ImmutableMap.of(
            Query.EXECUTE, QueryHandler.with(QueryInputExecution.class, this::executionResponse));

    onVolatileStart();

    addSubcomponent(processRepository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response executionResponse(
      QueryInputExecution queryInput, ApiRequestContext requestContext) {

    final OgcExecute executeRequest;
    try {
      // the schema of an execute request allows members that are not specified, they are ignored
      executeRequest =
          mapper
              .readerFor(OgcExecute.class)
              .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .readValue(queryInput.getRequestBody());
    } catch (IOException e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Could not parse request body", e);
      }
      throw new IllegalArgumentException(
          "Could not parse request body into a valid execute request.");
    }
    // the JSON value null
    if (Objects.isNull(executeRequest)) {
      throw new IllegalArgumentException("The request body is not a valid execute request.");
    }
    validateUris(executeRequest);

    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();
    String processId = queryInput.getProcessId();
    ProcessData process = processRepository.getDirect(apiData.getId(), processId);

    boolean async =
        isExecutedAsync(process, queryInput.supportsAsync(), queryInput.getPreferAsync());

    if (!async) {
      return executionResponseSync(queryInput, requestContext, process, executeRequest);
    }

    validateCallbacks(
        executeRequest,
        apiData
            .getExtension(ProcessesCoreConfiguration.class)
            .map(ProcessesCoreConfiguration::getAllowedCallbackUris)
            .orElse(List.of()));

    return executionResponseAsync(queryInput, requestContext, process, executeRequest);
  }

  // the process and the callbacks are URIs, the callbacks are sent with HTTP
  static void validateUris(OgcExecute executeRequest) {
    executeRequest
        .getProcess()
        .ifPresent(
            process -> {
              if (!isAbsoluteUri(process, false)) {
                throw new IllegalArgumentException(
                    "Invalid execute request: 'process' must be a URI, found '%s'."
                        .formatted(process));
              }
            });
    executeRequest
        .getSubscriber()
        .ifPresent(
            subscriber ->
                Map.of(
                        "successUri", subscriber.getSuccessUri(),
                        "inProgressUri", subscriber.getInProgressUri(),
                        "failedUri", subscriber.getFailedUri())
                    .forEach(
                        (name, uri) ->
                            uri.filter(value -> !isAbsoluteUri(value, true))
                                .ifPresent(
                                    value -> {
                                      throw new IllegalArgumentException(
                                          "Invalid execute request: the subscriber '%s' must be an HTTP URI, found '%s'."
                                              .formatted(name, value));
                                    })));
  }

  private static boolean isAbsoluteUri(String value, boolean http) {
    try {
      URI uri = new URI(value);
      return uri.isAbsolute()
          && (!http
              || (("http".equalsIgnoreCase(uri.getScheme())
                      || "https".equalsIgnoreCase(uri.getScheme()))
                  && Objects.nonNull(uri.getHost())));
    } catch (URISyntaxException e) {
      return false;
    }
  }

  // the callbacks of an asynchronous execution are only sent to the allowed URIs, otherwise a
  // client could direct requests of the server to any host the server can reach, including
  // services that are only reachable from the server
  static void validateCallbacks(OgcExecute executeRequest, List<String> allowedUris) {
    List<String> callbacks =
        executeRequest
            .getSubscriber()
            .map(
                subscriber ->
                    Stream.of(
                            subscriber.getSuccessUri(),
                            subscriber.getInProgressUri(),
                            subscriber.getFailedUri())
                        .flatMap(Optional::stream)
                        .toList())
            .orElse(List.of());
    if (callbacks.isEmpty()) {
      return;
    }
    if (allowedUris.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid execute request: this API does not send callbacks, the request must not have a subscriber.");
    }
    callbacks.stream()
        .filter(callback -> !AllowedUris.isAllowed(callback, allowedUris))
        .findFirst()
        .ifPresent(
            callback -> {
              throw new IllegalArgumentException(
                  "Invalid execute request: this API does not send callbacks to '%s'."
                      .formatted(callback));
            });
  }

  // Requirements 19 and 20: the preference only decides if the process supports both modes
  static boolean isExecutedAsync(OgcProcess process, boolean asyncSupported, boolean preferAsync) {
    List<JobControlOptions> jobControlOptions = process.getJobControlOptions();
    boolean canSync =
        jobControlOptions.isEmpty() || jobControlOptions.contains(JobControlOptions.SYNC_EXECUTE);
    boolean canAsync =
        asyncSupported && jobControlOptions.contains(JobControlOptions.ASYNC_EXECUTE);

    if (!canSync && !canAsync) {
      throw new IllegalArgumentException(
          "Process '%s' can only be executed asynchronously, which is not supported by this API."
              .formatted(process.getId()));
    }

    return canAsync && (!canSync || preferAsync);
  }

  private Response executionResponseSync(
      QueryInputExecution queryInput,
      ApiRequestContext requestContext,
      ProcessData process,
      OgcExecute executeRequest) {

    OgcApi api = requestContext.getApi();

    JobResultsFormatExtension outputFormat =
        api.getOutputFormat(
                JobResultsFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            // process outputs are never encoded in an arbitrary media type
            .filter(format -> !format.getMediaType().type().isWildcardType())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    long timeoutMillis =
        api.getData()
            .getExtension(ProcessesCoreConfiguration.class)
            .orElseThrow()
            .getSyncTimeoutMillis();
    Map<String, Object> processResults =
        processesExecutor.executeSync(
            api.getId(), process, executeRequest, executionContext(requestContext), timeoutMillis);

    if (!processResults.isEmpty()) {
      OgcApiJobResults resultsResponse =
          new ImmutableOgcApiJobResults.Builder().additionalProperties(processResults).build();

      return prepareSuccessResponse(
              requestContext,
              null,
              HeaderCaching.of(null, null, queryInput),
              null,
              HeaderContentDisposition.of(
                  String.format(
                      "%s.%s", process.getId(), outputFormat.getMediaType().fileExtension())),
              i18n.getLanguages())
          .entity(outputFormat.getEntity(resultsResponse, api, requestContext))
          .build();
    } else {
      // Requirement 34
      return prepareSuccessResponse(
              requestContext,
              null,
              HeaderCaching.of(null, null, queryInput),
              null,
              null,
              i18n.getLanguages())
          .status(Response.Status.NO_CONTENT)
          .build();
    }
  }

  // the context that the job carries to the steps: the profiles of the parameter profile, which
  // apply e.g. to the features in the outputs
  private static ExecutionRequestContext executionContext(ApiRequestContext requestContext) {
    Object profiles =
        requestContext.getQueryParameterSet().getTypedValues().get(QueryParameterProfile.PROFILE);
    return ExecutionRequestContext.ofProfiles(
        profiles instanceof List<?> list
            ? list.stream()
                .filter(Profile.class::isInstance)
                .map(profile -> ((Profile) profile).getId())
                .toList()
            : List.of());
  }

  private Response executionResponseAsync(
      QueryInputExecution queryInput,
      ApiRequestContext requestContext,
      ProcessData process,
      OgcExecute executeRequest) {

    OgcApi api = requestContext.getApi();

    JobStatusFormatExtension outputFormat =
        api.getOutputFormat(
                JobStatusFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    ProcessesCoreConfiguration processesCoreConfiguration =
        api.getData().getExtension(ProcessesCoreConfiguration.class).orElseThrow();

    OgcApiJobStatus statusInfo =
        processesExecutor.executeAsync(
            api.getId(),
            process,
            executeRequest,
            executionContext(requestContext),
            queryInput.getCallbackRetries(),
            processesCoreConfiguration.getTtlSeconds());

    // the response is not retrieved from the job, the job is the resource to monitor
    List<Link> links =
        new StatusInfoLinksGenerator().generateMonitorLinks(requestContext, 3, i18n, statusInfo);

    OgcApiJobStatusInfo statusInfoResponse =
        new ImmutableOgcApiJobStatusInfo.Builder().from(statusInfo).links(links).build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !MediaType.TEXT_HTML_TYPE.equals(outputFormat.getMediaType().type())
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(
                statusInfoResponse, OgcApiJobStatusInfo.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    Response.ResponseBuilder asyncResponse =
        prepareSuccessResponse(
                requestContext,
                queryInput.getIncludeLinkHeader() ? links : null,
                HeaderCaching.of(lastModified, etag, queryInput),
                null,
                HeaderContentDisposition.of(
                    String.format(
                        "%s.%s", process.getId(), outputFormat.getMediaType().fileExtension())),
                i18n.getLanguages())
            .status(Response.Status.CREATED)
            .location(
                URI.create(
                    requestContext
                        .getApiUriCustomizer()
                        .appendPathSegments("jobs", statusInfo.getId())
                        .toString()))
            .entity(outputFormat.getEntity(statusInfoResponse, api, requestContext));

    // the preference is only reported if the client sent it, otherwise the process is
    // asynchronous only
    if (queryInput.getPreferAsync()) {
      asyncResponse.header("Preference-Applied", "respond-async");
    }

    return asyncResponse.build();
  }
}
