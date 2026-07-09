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
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.processes.app.format.ProcessListLinksGenerator;
import de.ii.ogcapi.processes.domain.ProcessesQueriesHandler;
import de.ii.ogcapi.processes.domain.format.ProcessFormatExtension;
import de.ii.ogcapi.processes.domain.format.ProcessListFormatExtension;
import de.ii.ogcapi.processes.domain.model.Process;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.ogcapi.processes.domain.model.ProcessSummary;
import de.ii.ogcapi.processes.domain.model.rep.ImmutableOgcProcess;
import de.ii.ogcapi.processes.domain.model.rep.ImmutableOgcProcessList;
import de.ii.ogcapi.processes.domain.model.rep.ImmutableOgcProcessSummary;
import de.ii.ogcapi.processes.domain.model.rep.OgcProcess;
import de.ii.ogcapi.processes.domain.model.rep.OgcProcessList;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
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
import java.util.stream.Collectors;

@Singleton
@AutoBind
public class ProcessesQueriesHandlerImpl extends AbstractVolatileComposed
    implements ProcessesQueriesHandler {

  private final ProcessRepository processRepository;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final I18n i18n;

  @Inject
  public ProcessesQueriesHandlerImpl(
      I18n i18n,
      ExtensionRegistry extensionRegistry,
      ProcessRepository repository,
      VolatileRegistry volatileRegistry) {
    super(ProcessesQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.processRepository = repository;

    this.queryHandlers =
        ImmutableMap.of(
            Query.PROCESSES,
            QueryHandler.with(QueryInputProcesses.class, this::getProcessesResponse),
            Query.PROCESS,
            QueryHandler.with(QueryInputProcess.class, this::getProcessResponse));

    onVolatileStart();

    addSubcomponent(repository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getProcessesResponse(
      QueryInputProcesses queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();

    ProcessListFormatExtension outputFormat =
        api.getOutputFormat(
                ProcessListFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    final int offset = queryInput.getOffset();
    final int limit = queryInput.getLimit();
    final int defaultLimit = queryInput.getDefaultLimit();
    final List<String> processIds = processRepository.getAll().keySet().stream().toList();
    final int processesSize = processIds.size();

    final ProcessListLinksGenerator linkGenerator = new ProcessListLinksGenerator();

    List<Link> links =
        linkGenerator.generateLinks(
            requestContext.getUriCustomizer(),
            offset,
            limit,
            defaultLimit,
            processesSize,
            requestContext.getMediaType(),
            requestContext.getAlternateMediaTypes(),
            i18n,
            requestContext.getLanguage());

    OgcProcessList processList =
        ImmutableOgcProcessList.builder()
            .processes(
                processIds.stream()
                    .skip(offset)
                    .limit(limit)
                    .map(processRepository::get)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(process -> (ProcessSummary) process)
                    .map(
                        processSummary ->
                            ImmutableOgcProcessSummary.builder()
                                .id(processSummary.getId())
                                .title(processSummary.getTitle())
                                .version(processSummary.getVersion())
                                .jobControlOptions(processSummary.getJobControlOptions())
                                .links(
                                    linkGenerator.generateProcessLink(
                                        requestContext.getUriCustomizer(),
                                        processSummary.getId(),
                                        i18n,
                                        requestContext.getLanguage()))
                                .build())
                    .collect(Collectors.toList()))
            .links(links)
            .build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        ETag.from(processList, OgcProcessList.FUNNEL, outputFormat.getMediaType().label());
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("processes.%s", outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(processList, api, requestContext))
        .build();
  }

  private Response getProcessResponse(
      QueryInputProcess queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    String processId = queryInput.getProcessId();

    ProcessFormatExtension outputFormat =
        api.getOutputFormat(
                ProcessFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    // ToDo Links
    List<Link> links =
        new DefaultLinksGenerator()
            .generateLinks(
                requestContext.getUriCustomizer(),
                requestContext.getMediaType(),
                requestContext.getAlternateMediaTypes(),
                i18n,
                requestContext.getLanguage());

    Process process =
        processRepository
            .get(processId)
            .orElseThrow(() -> new NotFoundException("Unknown process: " + processId));

    OgcProcess processEntity = new ImmutableOgcProcess.Builder().from(process).links(links).build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !MediaType.TEXT_HTML_TYPE.equals(outputFormat.getMediaType().type())
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(processEntity, OgcProcess.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", processId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(processEntity, api, requestContext))
        .build();
  }
}
