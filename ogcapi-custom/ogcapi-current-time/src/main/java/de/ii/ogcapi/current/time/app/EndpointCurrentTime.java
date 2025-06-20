/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.current.time.app;

import static de.ii.ogcapi.foundation.domain.ApiSecurity.GROUP_DISCOVER_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.common.domain.CommonBuildingBlock;
import de.ii.ogcapi.common.domain.CommonConfiguration;
import de.ii.ogcapi.common.domain.ConformanceDeclarationFormatExtension;
import de.ii.ogcapi.common.domain.QueriesHandlerCommon;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.auth.domain.User;
import io.dropwizard.auth.Auth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * @title Conformance Declaration
 * @path conformance
 * @langEn The URIs of all conformance classes supported by the API. This information is provided to
 *     support 'generic' clients that want to access multiple OGC API implementations - and not
 *     'just' a specific API. For clients accessing only a single API, this information is in
 *     general not relevant and the OpenAPI definition describes the API in detail.
 * @langDe Die URIs aller von der API unterstützten Konformitätsklassen. Diese Informationen werden
 *     bereitgestellt, um "generische" Clients zu unterstützen, die auf mehrere
 *     OGC-API-Implementierungen zugreifen wollen - und nicht "nur" auf eine bestimmte API. Für
 *     Clients, die nur auf eine einzige API zugreifen, ist diese Information im Allgemeinen nicht
 *     relevant und die OpenAPI-Definition beschreibt die API im Detail.
 * @ref:formats {@link de.ii.ogcapi.common.domain.ConformanceDeclarationFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointCurrentTime extends Endpoint {

  private static final List<String> TAGS = ImmutableList.of("Capabilities");

  @Inject
  public EndpointCurrentTime(
      ExtensionRegistry extensionRegistry, QueriesHandlerCommon queryHandler) {
    super(extensionRegistry);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CommonConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(ConformanceDeclarationFormatExtension.class);
    }
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("time")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_CONFORMANCE);
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, "/time");
    String operationSummary = "get current time";
    Optional<String> operationDescription =
        Optional.of(
            "The URIs of all conformance classes supported by the server. "
                + "This information is provided to support 'generic' clients that want to access multiple "
                + "OGC API implementations - and not 'just' a specific API. For clients accessing only a single "
                + "API, this information is in general not relevant and the OpenAPI definition details the "
                + "required information about the API.");
    String path = "/time";
    ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
        new ImmutableOgcApiResourceAuxiliary.Builder().path(path);
    ApiOperation.getResource(
            apiData,
            path,
            false,
            queryParameters,
            ImmutableList.of(),
            getResponseContent(apiData),
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("getTimeDeclaration"),
            GROUP_DISCOVER_READ,
            TAGS,
            CommonBuildingBlock.MATURITY,
            CommonBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  @GET
  public Response getTime(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @QueryParam("timezone") String timezoneParam) {

    ZoneId timezone = (timezoneParam == null || timezoneParam.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(timezoneParam);

    String now = ZonedDateTime.now(timezone).toString();


    String requestType = requestContext.getMediaType().type().getSubtype();
    if(requestType.equals("json")){
      return Response.ok().type("application/json").entity(Map.of("time", now, "timezone", timezone)).build();
    };
    if(requestType.equals("html")){
      return Response.ok().type("text/html").entity("<html><body><h1>Aktuelle Uhrzeit</h1><p>" + now + "</p></body></html>").build();
    }
    return Response.status(Status.UNSUPPORTED_MEDIA_TYPE).build();

  }
}
