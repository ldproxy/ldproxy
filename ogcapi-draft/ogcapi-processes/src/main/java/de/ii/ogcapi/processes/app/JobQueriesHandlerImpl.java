/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.processes.app.format.StatusInfoLinksGenerator;
import de.ii.ogcapi.processes.domain.JobQueriesHandler;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.format.ResultsFormatExtension;
import de.ii.ogcapi.processes.domain.format.StatusInfoFormatExtension;
import de.ii.ogcapi.processes.domain.format.ValuesFormatExtension;
import de.ii.ogcapi.processes.domain.model.Process;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.ogcapi.processes.domain.model.StatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcResults;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcValues;
import de.ii.ogcapi.processes.domain.model.ogc.OgcResults;
import de.ii.ogcapi.processes.domain.model.ogc.OgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.OgcValues;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Singleton
@AutoBind
public class JobQueriesHandlerImpl extends AbstractVolatileComposed implements JobQueriesHandler {

  private final ProcessRepository processRepository;
  private final ProcessesExecutor processesExecutor;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final I18n i18n;

  @Inject
  public JobQueriesHandlerImpl(
      I18n i18n,
      ExtensionRegistry extensionRegistry,
      ProcessRepository processRepository,
      ProcessesExecutor processesExecutor,
      VolatileRegistry volatileRegistry) {
    super(JobQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.processRepository = processRepository;
    this.processesExecutor = processesExecutor;

    this.queryHandlers =
        ImmutableMap.of(
            Query.JOB,
            QueryHandler.with(QueryInputJob.class, this::getJobResponse),
            Query.RESULTS,
            QueryHandler.with(QueryInputResults.class, this::getJobResultsResponse),
            Query.RESULTS_SPECIFIC,
            QueryHandler.with(QueryInputResultsSpecfic.class, this::getJobResultsResponseSpecific),
            Query.RESULTS_SPECIFIC_N,
            QueryHandler.with(
                QueryInputResultsSpecficN.class, this::getJobResultsResponseSpecificN),
            Query.DISMISS,
            QueryHandler.with(QueryInputDismiss.class, this::dismissJobResponse));

    onVolatileStart();

    addSubcomponent(processRepository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getJobResponse(QueryInputJob queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    String jobId = queryInput.getJobId();

    StatusInfoFormatExtension outputFormat =
        api.getOutputFormat(
                StatusInfoFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    StatusInfo statusInfo =
        processesExecutor
            .getStatusInfo(jobId)
            .orElseThrow(() -> new NotFoundException("Unknown job: " + jobId));

    final StatusInfoLinksGenerator linkGenerator = new StatusInfoLinksGenerator();
    List<Link> links =
        linkGenerator.generateLinks(
            requestContext.getUriCustomizer(), i18n, requestContext.getLanguage(), statusInfo, 0);

    OgcStatusInfo ogcStatusInfoResponse =
        new ImmutableOgcStatusInfo.Builder().from(statusInfo).links(links).build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !MediaType.TEXT_HTML_TYPE.equals(outputFormat.getMediaType().type())
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(
                ogcStatusInfoResponse, OgcStatusInfo.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(ogcStatusInfoResponse, api, requestContext))
        .build();
  }

  private Response getJobResultsResponse(
      QueryInputResults queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    ResultsFormatExtension outputFormat =
        api.getOutputFormat(
                ResultsFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    String jobId = queryInput.getJobId();
    StatusInfo statusInfo = getStatusInfo(jobId);
    Map<String, Object> jobResults = getResults(jobId, statusInfo);

    OgcResults results = new ImmutableOgcResults.Builder().additionalProperties(jobResults).build();

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(null, null, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(results, api, requestContext))
        .build();
  }

  private Response getJobResultsResponseSpecific(
      QueryInputResultsSpecfic queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    ValuesFormatExtension outputFormat =
        api.getOutputFormat(
                ValuesFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    String jobId = queryInput.getJobId();
    String outputId = queryInput.getOutputId();
    StatusInfo statusInfo = getStatusInfo(jobId);
    Map<String, Object> jobResults = getResults(jobId, statusInfo);
    validateOutputId(jobId, outputId, statusInfo, jobResults);

    OgcValues results =
        new ImmutableOgcValues.Builder().inlineOrRefValue(jobResults.get(outputId)).build();

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(null, null, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(results, api, requestContext))
        .build();
  }

  private Response getJobResultsResponseSpecificN(
      QueryInputResultsSpecficN queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    ValuesFormatExtension outputFormat =
        api.getOutputFormat(
                ValuesFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    String jobId = queryInput.getJobId();
    String outputId = queryInput.getOutputId();
    int indexN = queryInput.getIndexN();
    StatusInfo statusInfo = getStatusInfo(jobId);
    Map<String, Object> jobResults = getResults(jobId, statusInfo);
    validateOutputId(jobId, outputId, statusInfo, jobResults);

    int maxOccurs = getMaxOccurs(jobId, outputId, statusInfo);

    Object specficResult;

    if (maxOccurs < (indexN + 1)) {
      throw new BadRequestException(
          "Out-of-bound: "
              + "Attempting to access element at index "
              + indexN
              + " of output '"
              + outputId
              + "' with maxOccurs "
              + maxOccurs
              + ".");
    }

    if (indexN <= 1) {
      specficResult = jobResults.get(outputId);
    } else {
      if (!(jobResults.get(outputId) instanceof ArrayList<?>)) {
        throw new ServerErrorException("Result of output '" + outputId + "' is not an Array.", 500);
      }

      specficResult = ((ArrayList<?>) jobResults.get(outputId)).get(indexN);
    }

    OgcValues results = new ImmutableOgcValues.Builder().inlineOrRefValue(specficResult).build();

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(null, null, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(results, api, requestContext))
        .build();
  }

  private Response dismissJobResponse(
      QueryInputDismiss queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    StatusInfoFormatExtension outputFormat =
        api.getOutputFormat(
                StatusInfoFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    String jobId = queryInput.getJobId();
    StatusInfo statusInfo = processesExecutor.dismissJob(jobId).orElseThrow();

    final StatusInfoLinksGenerator linkGenerator = new StatusInfoLinksGenerator();
    List<Link> links =
        linkGenerator.generateLinks(
            requestContext.getUriCustomizer(), i18n, requestContext.getLanguage(), statusInfo, 1);

    OgcStatusInfo ogcStatusInfoResponse =
        new ImmutableOgcStatusInfo.Builder().from(statusInfo).links(links).build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !MediaType.TEXT_HTML_TYPE.equals(outputFormat.getMediaType().type())
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(
                ogcStatusInfoResponse, OgcStatusInfo.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(ogcStatusInfoResponse, api, requestContext))
        .build();
  }

  /*** Helper methods ***/
  private StatusInfo getStatusInfo(String jobId) {
    return processesExecutor.getStatusInfo(jobId).orElseThrow();
  }

  private Map<String, Object> getResults(String jobId, StatusInfo statusInfo) {
    return processesExecutor
        .getResults(jobId)
        .orElseThrow(() -> new NotFoundException("Job '" + jobId + " ' did not finish or failed."));
  }

  private void validateOutputId(
      String jobId, String outputId, StatusInfo statusInfo, Map<String, Object> jobResults) {
    String processId = statusInfo.getProcessId();
    Process executedProcess = processRepository.getDirect(processId);

    if (!executedProcess.getOutputs().containsKey(outputId)) {
      throw new NotFoundException(
          "The output of the process '"
              + processId
              + "', which was executed by job '"
              + jobId
              + "', does not contain an output '"
              + outputId
              + "'.");
    }

    if (!jobResults.containsKey(outputId)) {
      throw new NotFoundException(
          "The results of job '" + jobId + "' do not contain an output '" + outputId + "'.");
    }
  }

  private int getMaxOccurs(String jobId, String outputId, StatusInfo statusInfo) {
    String processId = statusInfo.getProcessId();
    Process executedProcess = processRepository.getDirect(processId);
    return executedProcess.getOutputs().get(outputId).getMaxOccurs();
  }
}
