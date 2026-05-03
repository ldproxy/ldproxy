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
import de.ii.ogcapi.codelists.domain.CodelistFormatExtension;
import de.ii.ogcapi.codelists.domain.CodelistsConfiguration;
import de.ii.ogcapi.codelists.domain.ImmutableQueryInputCodelist;
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
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Codelist
 * @path codelists/{codelistId}
 * @langEn Fetches the codeliste with identifier `codelistId`. The set of available codelists can be
 *     retrieved at `codelists`.
 * @langDe Holt die Codelist mit dem Bezeichner `codelistId`. Die Menge der verfügbaren Codelisten
 *     kann unter `codelists` abgerufen werden.
 * @ref:formats {@link de.ii.ogcapi.codelists.domain.CodelistFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointCodelist extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointCodelist.class);

  private static final List<String> TAGS = ImmutableList.of("Discover and fetch codelists");

  private final QueriesHandlerCodelists queryHandler;

  @Inject
  public EndpointCodelist(
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
      formats = extensionRegistry.getExtensionsForType(CodelistFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("codelists")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_RESOURCE);
    String path = "/codelists/{codelistId}";
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    if (pathParameters.stream().noneMatch(param -> param.getName().equals("codelistId"))) {
      LOGGER.error(
          "Path parameter 'codelistId' missing for resource at path '{}'. The GET method will not be available.",
          path);
    } else {
      String operationSummary = "fetch the codelist `{codelistId}`";
      Optional<String> operationDescription =
          Optional.of(
              "Fetches the codelist with identifier `codelistId`. The set of "
                  + "available codelists can be retrieved at `/codelists`.");
      ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
          new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);
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
              getOperationId("getCodelist"),
              GROUP_CODELISTS_READ,
              TAGS,
              CodelistsBuildingBlock.MATURITY,
              CodelistsBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  /**
   * Fetch a resource by id
   *
   * @param codelistId the local identifier of a specific resource
   * @return the codelist
   */
  @Path("/{codelistId}")
  @GET
  @Produces(MediaType.WILDCARD)
  public Response getResource(
      @PathParam("codelistId") String codelistId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {
    QueriesHandlerCodelists.QueryInputCodelist queryInput =
        ImmutableQueryInputCodelist.builder()
            .from(getGenericQueryInput(api.getData()))
            .codelistId(codelistId)
            .build();

    return queryHandler.handle(Query.CODELIST, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
