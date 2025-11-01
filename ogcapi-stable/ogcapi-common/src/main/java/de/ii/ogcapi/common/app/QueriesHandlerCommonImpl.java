/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.common.domain.ApiDefinitionFormatExtension;
import de.ii.ogcapi.common.domain.ConformanceDeclaration;
import de.ii.ogcapi.common.domain.ConformanceDeclarationExtension;
import de.ii.ogcapi.common.domain.ConformanceDeclarationFormatExtension;
import de.ii.ogcapi.common.domain.ImmutableConformanceDeclaration;
import de.ii.ogcapi.common.domain.ImmutableLandingPage.Builder;
import de.ii.ogcapi.common.domain.LandingPage;
import de.ii.ogcapi.common.domain.LandingPageExtension;
import de.ii.ogcapi.common.domain.LandingPageFormatExtension;
import de.ii.ogcapi.common.domain.OgcApiExtent;
import de.ii.ogcapi.common.domain.QueriesHandlerCommon;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.PageRepresentation;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.xtraplatform.base.domain.ETag;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.immutables.value.Value;

@Singleton
@AutoBind
public class QueriesHandlerCommonImpl implements QueriesHandlerCommon {

  public enum Query implements QueryIdentifier {
    LANDING_PAGE,
    CONFORMANCE_DECLARATION,
    API_DEFINITION
  }

  @Value.Immutable
  public interface QueryInputLandingPage extends QueryInput {
    boolean getIncludeLinkHeader();

    List<Link> getAdditionalLinks();
  }

  @Value.Immutable
  public interface QueryInputConformance extends QueryInput {
    boolean getIncludeLinkHeader();
  }

  @Value.Immutable
  public interface QueryInputFile extends QueryInput {
    Optional<String> getSubPath();
  }

  private final ExtensionRegistry extensionRegistry;
  private final I18n i18n;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;

  @Inject
  public QueriesHandlerCommonImpl(ExtensionRegistry extensionRegistry, I18n i18n) {
    this.extensionRegistry = extensionRegistry;
    this.i18n = i18n;
    this.queryHandlers =
        ImmutableMap.of(
            Query.LANDING_PAGE,
            QueryHandler.with(QueryInputLandingPage.class, this::getLandingPageResponse),
            Query.CONFORMANCE_DECLARATION,
            QueryHandler.with(QueryInputConformance.class, this::getConformanceResponse),
            Query.API_DEFINITION,
            QueryHandler.with(QueryInput.class, this::getApiDefinitionResponse));
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getLandingPageResponse(
      QueryInputLandingPage queryInput, ApiRequestContext requestContext) {
    final LandingPageLinksGenerator linksGenerator = new LandingPageLinksGenerator();
    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();

    List<Link> links =
        linksGenerator.generateLinks(
            requestContext.getUriCustomizer().copy(),
            Optional.empty(), // TODO: support schema links, e.g. for WFS provider new
            // WFSRequest(service.getWfsAdapter(), new
            // DescribeFeatureType()).getAsUrl()
            requestContext.getMediaType(),
            requestContext.getAlternateMediaTypes(),
            i18n,
            requestContext.getLanguage());

    Builder builder =
        new Builder()
            .title(apiData.getLabel())
            .description(apiData.getDescription().orElse(""))
            .attribution(apiData.getMetadata().flatMap(ApiMetadata::getAttribution))
            .externalDocs(apiData.getExternalDocs())
            .extent(OgcApiExtent.of(api.getSpatialExtent(), api.getTemporalExtent()))
            .links(links)
            .addAllLinks(queryInput.getAdditionalLinks());

    for (LandingPageExtension ogcApiLandingPageExtension : getDatasetExtenders()) {
      builder =
          ogcApiLandingPageExtension.process(
              builder,
              api,
              requestContext.getUriCustomizer().copy(),
              requestContext.getMediaType(),
              requestContext.getAlternateMediaTypes(),
              requestContext.getLanguage());
    }

    LandingPageFormatExtension outputFormatExtension =
        api.getOutputFormat(
                LandingPageFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type {0} cannot be generated.",
                            requestContext.getMediaType().type())));

    LandingPage apiLandingPage = builder.build();

    Object entity =
        outputFormatExtension.getEntity(apiLandingPage, requestContext.getApi(), requestContext);

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !outputFormatExtension.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(
                apiLandingPage,
                PageRepresentation.FUNNEL,
                outputFormatExtension.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? apiLandingPage.getLinks() : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format(
                    "landing-page.%s", outputFormatExtension.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(entity)
        .build();
  }

  private Response getConformanceResponse(
      QueryInputConformance queryInput, ApiRequestContext requestContext) {
    List<ConformanceClass> conformanceClasses =
        getConformanceClasses().stream()
            .filter(
                conformanceClass ->
                    conformanceClass.isEnabledForApi(requestContext.getApi().getData()))
            .collect(Collectors.toList());

    List<Link> links =
        new ConformanceDeclarationLinksGenerator()
            .generateLinks(
                requestContext.getUriCustomizer().copy(),
                requestContext.getMediaType(),
                requestContext.getAlternateMediaTypes(),
                i18n,
                requestContext.getLanguage());

    ConformanceDeclarationFormatExtension outputFormatExtension =
        requestContext
            .getApi()
            .getOutputFormat(
                ConformanceDeclarationFormatExtension.class,
                requestContext.getMediaType(),
                Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    ImmutableConformanceDeclaration.Builder builder =
        new ImmutableConformanceDeclaration.Builder()
            .links(links)
            .conformsTo(
                conformanceClasses.stream()
                    .map(
                        conformanceClass ->
                            conformanceClass.getConformanceClassUris(
                                requestContext.getApi().getData()))
                    .flatMap(List::stream)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toUnmodifiableList()));

    for (ConformanceDeclarationExtension ogcApiConformanceDeclarationExtension :
        getConformanceExtenders()) {
      builder =
          ogcApiConformanceDeclarationExtension.process(
              builder,
              requestContext.getApi().getData(),
              requestContext.getUriCustomizer().copy(),
              requestContext.getMediaType(),
              requestContext.getAlternateMediaTypes(),
              requestContext.getLanguage());
    }

    ConformanceDeclaration conformanceDeclaration = builder.build();

    Object entity =
        outputFormatExtension.getEntity(
            conformanceDeclaration, requestContext.getApi(), requestContext);
    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !outputFormatExtension.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || requestContext
                    .getApi()
                    .getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(
                conformanceDeclaration,
                ConformanceDeclaration.FUNNEL,
                outputFormatExtension.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? conformanceDeclaration.getLinks() : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format(
                    "conformance-declaration.%s",
                    outputFormatExtension.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(entity)
        .build();
  }

  private Response getApiDefinitionResponse(
      QueryInput queryInput, ApiRequestContext requestContext) {

    ApiDefinitionFormatExtension outputFormatExtension =
        requestContext
            .getApi()
            .getOutputFormat(
                ApiDefinitionFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    Date lastModified = getLastModified(queryInput);
    // TODO support ETag
    EntityTag etag = null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    // TODO support headers
    return outputFormatExtension.getResponse(requestContext.getApi().getData(), requestContext);
  }

  private List<LandingPageExtension> getDatasetExtenders() {
    return extensionRegistry.getExtensionsForType(LandingPageExtension.class);
  }

  private List<ConformanceDeclarationExtension> getConformanceExtenders() {
    return extensionRegistry.getExtensionsForType(ConformanceDeclarationExtension.class);
  }

  private List<ConformanceClass> getConformanceClasses() {
    return extensionRegistry.getExtensionsForType(ConformanceClass.class);
  }
}
