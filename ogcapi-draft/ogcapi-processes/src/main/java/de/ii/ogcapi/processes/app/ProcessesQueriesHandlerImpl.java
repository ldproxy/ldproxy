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
import de.ii.ogcapi.processes.app.json.ProcessDescriptionLinksGenerator;
import de.ii.ogcapi.processes.domain.ProcessDescriptionsFormatExtension;
import de.ii.ogcapi.processes.domain.ProcessDescriptionsLinksGenerator;
import de.ii.ogcapi.processes.domain.ProcessesQueriesHandler;
import de.ii.ogcapi.processes.domain.model.ImmutableProcessDescriptionReduced;
import de.ii.ogcapi.processes.domain.model.ImmutableProcessDescriptions;
import de.ii.ogcapi.processes.domain.model.ProcessDescriptionRepository;
import de.ii.ogcapi.processes.domain.model.ProcessDescriptions;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.core.EntityTag;
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

  private final ProcessDescriptionRepository processDescriptionRepository;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final I18n i18n;

  @Inject
  public ProcessesQueriesHandlerImpl(
      I18n i18n,
      ExtensionRegistry extensionRegistry,
      ProcessDescriptionRepository repository,
      VolatileRegistry volatileRegistry) {
    super(ProcessesQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.processDescriptionRepository = repository;

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

    ProcessDescriptionsFormatExtension outputFormat =
        api.getOutputFormat(
                ProcessDescriptionsFormatExtension.class,
                requestContext.getMediaType(),
                Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    final ProcessDescriptionLinksGenerator linkGenerator = new ProcessDescriptionLinksGenerator();

    final int offset = queryInput.getOffset();
    final int limit = queryInput.getLimit();
    final int defaultLimit = queryInput.getDefaultLimit();
    final List<String> processIds =
        processDescriptionRepository.getAll().keySet().stream().toList();
    final int processesSize = processIds.size();

    List<Link> links =
        new ProcessDescriptionsLinksGenerator()
            .generateLinks(
                requestContext.getUriCustomizer(),
                offset,
                limit,
                defaultLimit,
                processesSize,
                requestContext.getMediaType(),
                requestContext.getAlternateMediaTypes(),
                true,
                i18n,
                requestContext.getLanguage());

    ProcessDescriptions processDescriptions =
        ImmutableProcessDescriptions.builder()
            .processes(
                processIds.stream()
                    .skip(offset)
                    .limit(limit)
                    .map(processDescriptionRepository::get)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(
                        processDescription ->
                            ImmutableProcessDescriptionReduced.builder()
                                .id(processDescription.getId())
                                .title(processDescription.getTitle())
                                .version(processDescription.getVersion())
                                .jobControlOptions(processDescription.getJobControlOptions())
                                .links(
                                    linkGenerator.generateProcessDescriptionLinks(
                                        requestContext.getUriCustomizer(),
                                        processDescription.getId(),
                                        i18n,
                                        requestContext.getLanguage()))
                                .build())
                    .collect(Collectors.toList()))
            .links(links)
            .build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        ETag.from(
            processDescriptions, ProcessDescriptions.FUNNEL, outputFormat.getMediaType().label());
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
        .entity(outputFormat.getEntity(processDescriptions, api, requestContext))
        .build();
  }

  private Response getProcessResponse(
      QueryInputProcess queryInput, ApiRequestContext requestContext) {
    return Response.ok().build();
  }
}
