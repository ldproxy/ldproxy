/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.async.domain.JobQueriesHandler;
import de.ii.ogcapi.async.domain.JobResultValuesFormatExtension;
import de.ii.ogcapi.common.domain.async.ImmutableOgcApiJobResultValues;
import de.ii.ogcapi.common.domain.async.ImmutableOgcApiJobResults;
import de.ii.ogcapi.common.domain.async.ImmutableOgcApiJobStatusInfo;
import de.ii.ogcapi.common.domain.async.JobResultsFormatExtension;
import de.ii.ogcapi.common.domain.async.JobStatusFormatExtension;
import de.ii.ogcapi.common.domain.async.OgcApiJobResultValues;
import de.ii.ogcapi.common.domain.async.OgcApiJobResults;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatusInfo;
import de.ii.ogcapi.common.domain.async.StatusInfoLinksGenerator;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.xtralink.jobs.Identifiers.Status;
import de.ii.xtraplatform.base.domain.ETag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Singleton
@AutoBind
public class JobQueriesHandlerImpl implements JobQueriesHandler {

  private final AsyncJobs jobAccess;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final I18n i18n;

  @Inject
  public JobQueriesHandlerImpl(I18n i18n, AsyncJobs jobAccess) {
    this.i18n = i18n;
    this.jobAccess = jobAccess;

    this.queryHandlers =
        ImmutableMap.of(
            Query.JOB,
            QueryHandler.with(QueryInputJob.class, this::getJobResponse),
            Query.RESULTS,
            QueryHandler.with(QueryInputResults.class, this::getJobResultsResponse),
            Query.RESULTS_SPECIFIC,
            QueryHandler.with(QueryInputResultsSpecific.class, this::getJobResultsResponseSpecific),
            Query.RESULTS_SPECIFIC_N,
            QueryHandler.with(
                QueryInputResultsSpecificN.class, this::getJobResultsResponseSpecificN),
            Query.DISMISS,
            QueryHandler.with(QueryInputDismiss.class, this::dismissJobResponse));
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getJobResponse(QueryInputJob queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    String jobId = queryInput.getJobId();

    JobStatusFormatExtension outputFormat =
        api.getOutputFormat(
                JobStatusFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    OgcApiJobStatus statusInfo = getStatusInfo(jobId);

    // Requirement 115 (after
    // https://github.com/opengeospatial/ogcapi-processes/issues/620#issuecomment-5478959257 is
    // applied)
    if (statusInfo.getStatus() == Status.DISMISSED) {
      throw new NotFoundException(
          "The job has been dismissed and is no longer available: " + jobId);
    }

    final StatusInfoLinksGenerator linkGenerator = new StatusInfoLinksGenerator();
    List<Link> links =
        linkGenerator.generateLinks(
            requestContext.getUriCustomizer(), i18n, requestContext.getLanguage(), statusInfo, 0);

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

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(statusInfoResponse, api, requestContext))
        .build();
  }

  private Response getJobResultsResponse(
      QueryInputResults queryInput, ApiRequestContext requestContext) {

    String jobId = queryInput.getJobId();
    Map<String, Object> jobResults = jobAccess.getResults(jobId);

    // Requirement 34
    if (jobResults.isEmpty()) {
      return emptyResponse(requestContext, queryInput, jobId);
    }

    OgcApi api = requestContext.getApi();
    JobResultsFormatExtension outputFormat =
        api.getOutputFormat(
                JobResultsFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    OgcApiJobResults resultsResponse =
        new ImmutableOgcApiJobResults.Builder().additionalProperties(jobResults).build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !MediaType.TEXT_HTML_TYPE.equals(outputFormat.getMediaType().type())
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(
                resultsResponse, OgcApiJobResults.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(resultsResponse, api, requestContext))
        .build();
  }

  private Response getJobResultsResponseSpecific(
      QueryInputResultsSpecific queryInput, ApiRequestContext requestContext) {

    String jobId = queryInput.getJobId();
    String outputId = queryInput.getOutputId();
    Object output = jobAccess.getResultsSpecific(jobId, outputId);

    return basicResponse(requestContext, queryInput, jobId, output);
  }

  // Limitation: Requirement 50 ("0-th"-result) is not supported!
  private Response getJobResultsResponseSpecificN(
      QueryInputResultsSpecificN queryInput, ApiRequestContext requestContext) {
    String jobId = queryInput.getJobId();
    String outputId = queryInput.getOutputId();
    int indexN = queryInput.getIndexN();
    Object output = jobAccess.getResultsSpecificN(jobId, outputId, indexN);

    return basicResponse(requestContext, queryInput, jobId, output);
  }

  private Response dismissJobResponse(
      QueryInputDismiss queryInput, ApiRequestContext requestContext) {
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

    String jobId = queryInput.getJobId();
    OgcApiJobStatus statusInfo =
        jobAccess
            .dismissJob(jobId)
            .orElseThrow(() -> new NotFoundException("Unknown job: " + jobId));

    final StatusInfoLinksGenerator linkGenerator = new StatusInfoLinksGenerator();
    List<Link> links =
        linkGenerator.generateLinks(
            requestContext.getUriCustomizer(), i18n, requestContext.getLanguage(), statusInfo, 1);

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

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(statusInfoResponse, api, requestContext))
        .build();
  }

  /*** Helper methods ***/
  private OgcApiJobStatus getStatusInfo(String jobId) {
    return jobAccess
        .getStatusInfo(jobId)
        .orElseThrow(() -> new NotFoundException("Unknown job: " + jobId));
  }

  private Response emptyResponse(
      ApiRequestContext requestContext, QueryInput queryInput, String jobId) {
    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(null, null, queryInput),
            null,
            HeaderContentDisposition.of(jobId),
            i18n.getLanguages())
        .build();
  }

  private Response basicResponse(
      ApiRequestContext requestContext, QueryInput queryInput, String jobId, Object output) {
    OgcApi api = requestContext.getApi();
    JobResultValuesFormatExtension outputFormat =
        api.getOutputFormat(
                JobResultValuesFormatExtension.class,
                requestContext.getMediaType(),
                Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    OgcApiJobResultValues valuesResponse =
        new ImmutableOgcApiJobResultValues.Builder().inlineOrRefValues(output).build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !MediaType.TEXT_HTML_TYPE.equals(outputFormat.getMediaType().type())
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(
                valuesResponse, OgcApiJobResultValues.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", jobId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(valuesResponse, api, requestContext))
        .build();
  }
}
