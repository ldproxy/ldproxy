/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.infra;

import static de.ii.ogcapi.foundation.domain.ApiSecurity.GROUP_DISCOVER_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.common.app.QueriesHandlerCommonImpl.Query;
import de.ii.ogcapi.common.domain.ApiDefinitionFormatExtension;
import de.ii.ogcapi.common.domain.CommonBuildingBlock;
import de.ii.ogcapi.common.domain.CommonConfiguration;
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
import java.util.List;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @title API Definition
 * @path api
 * @langEn Provides the OpenAPI definition.
 * @langDe Stellt die OpenAPI-Definition bereit.
 * @ref:formats {@link de.ii.ogcapi.common.domain.ApiDefinitionFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointDefinition extends Endpoint {

  private final QueriesHandlerCommon queryHandler;

  @Inject
  public EndpointDefinition(
      ExtensionRegistry extensionRegistry, QueriesHandlerCommon queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CommonConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(ApiDefinitionFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("api")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_API_DEFINITION);
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, "/api");
    String operationSummary = "API definition";
    String path = "/api";
    ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder1 =
        new ImmutableOgcApiResourceAuxiliary.Builder().path(path);
    ApiOperation.getResource(
            apiData,
            path,
            false,
            queryParameters,
            ImmutableList.of(),
            getResponseContent(apiData),
            operationSummary,
            Optional.empty(),
            Optional.empty(),
            getOperationId("getApiDefinition"),
            GROUP_DISCOVER_READ,
            ImmutableList.of(),
            CommonBuildingBlock.MATURITY,
            CommonBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder1.putOperations("GET", operation));
    definitionBuilder.putResources(path, resourceBuilder1.build());

    return definitionBuilder.build();
  }

  @GET
  public Response getApiDefinition(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext ogcApiContext) {

    return queryHandler.handle(
        Query.API_DEFINITION, getGenericQueryInput(api.getData()), ogcApiContext);
  }
}
