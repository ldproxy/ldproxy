/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.infra;

import static de.ii.ogcapi.codelists.domain.QueriesHandlerCodelists.GROUP_CODELISTS_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.codelists.app.CodelistsBuildingBlock;
import de.ii.ogcapi.codelists.domain.CodelistsConfiguration;
import de.ii.ogcapi.codelists.domain.CodelistsFormatExtension;
import de.ii.ogcapi.codelists.domain.ImmutableQueryInputCodelists;
import de.ii.ogcapi.codelists.domain.QueriesHandlerCodelists;
import de.ii.ogcapi.codelists.domain.QueriesHandlerCodelists.Query;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceSet;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Codelists
 * @path codelists
 * @langEn This operation fetches the codelists available for this API.
 * @langDe Die Operation ruft die Codelisten in der API ab.
 * @ref:formats {@link de.ii.ogcapi.codelists.domain.CodelistsFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointCodelists extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointCodelists.class);

  private static final List<String> TAGS = ImmutableList.of("Discover and fetch codelists");
  private final QueriesHandlerCodelists queryHandler;

  @Inject
  public EndpointCodelists(
      ExtensionRegistry extensionRegistry, QueriesHandlerCodelists queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CodelistsConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(CodelistsFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("codelists")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_RESOURCES);
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, "/codelists");
    String operationSummary = "the list of available codelists";
    Optional<String> operationDescription =
        Optional.of(
            "This operation fetches the list of codelists that are used in this API. For each "
                + "codelist the id and a link to the codelist is provided.");
    String path = "/codelists";
    ImmutableOgcApiResourceSet.Builder resourceBuilderSet =
        new ImmutableOgcApiResourceSet.Builder().path(path).subResourceType("Codelist");
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
            getOperationId("getCodelists"),
            GROUP_CODELISTS_READ,
            TAGS,
            CodelistsBuildingBlock.MATURITY,
            CodelistsBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilderSet.putOperations("GET", operation));
    definitionBuilder.putResources(path, resourceBuilderSet.build());

    return definitionBuilder.build();
  }

  /**
   * fetch all available resources
   *
   * @return all resources in a JSON resources object
   */
  @GET
  @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML})
  public Response getResources(@Context OgcApi api, @Context ApiRequestContext requestContext) {
    QueriesHandlerCodelists.QueryInputCodelists queryInput =
        ImmutableQueryInputCodelists.builder().from(getGenericQueryInput(api.getData())).build();

    return queryHandler.handle(Query.CODELISTS, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
