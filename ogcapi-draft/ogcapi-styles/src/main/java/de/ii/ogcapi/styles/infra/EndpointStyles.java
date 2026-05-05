/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.infra;

import static de.ii.ogcapi.styles.domain.QueriesHandlerStyles.GROUP_STYLES_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceSet;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.styles.app.StylesBuildingBlock;
import de.ii.ogcapi.styles.domain.ImmutableQueryInputStyles;
import de.ii.ogcapi.styles.domain.QueriesHandlerStyles;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.ogcapi.styles.domain.StylesFormatExtension;
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

/**
 * @title Styles
 * @path styles
 * @langEn This operation fetches the list of styles. For each style the id, a title, links to the
 *     stylesheet of the style in each supported encoding, and the link to the metadata is provided.
 * @langDe Mit dieser Operation wird die Liste der Styles abgerufen. Für jeden Style werden die ID,
 *     ein Titel, Links zum Stylesheet des Styles in jeder unterstützten Kodierung und der Link zu
 *     den Metadaten bereitgestellt.
 * @ref:formats {@link de.ii.ogcapi.styles.domain.StylesFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointStyles extends Endpoint implements ConformanceClass, ApiExtensionHealth {

  private static final List<String> TAGS = ImmutableList.of("Discover and fetch styles");

  private final QueriesHandlerStyles queryHandler;

  @Inject
  public EndpointStyles(ExtensionRegistry extensionRegistry, QueriesHandlerStyles queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-styles-1/0.0/conf/core");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(StylesFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("styles")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_STYLES);
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, "/styles");
    String operationSummary = "lists the available styles";
    Optional<String> operationDescription =
        Optional.of(
            "This operation fetches the set of styles available. "
                + "For each style the id, a title, links to the stylesheet of the style in each supported encoding, "
                + "and the link to the metadata is provided.");
    String path = "/styles";
    ImmutableOgcApiResourceSet.Builder resourceBuilderSet =
        new ImmutableOgcApiResourceSet.Builder().path(path).subResourceType("Style");
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
            getOperationId("getStyles"),
            GROUP_STYLES_READ,
            TAGS,
            StylesBuildingBlock.MATURITY,
            StylesBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilderSet.putOperations("GET", operation));
    definitionBuilder.putResources(path, resourceBuilderSet.build());

    return definitionBuilder.build();
  }

  /**
   * fetch all available styles for the service
   *
   * @return all styles in a JSON styles object or an HTML page
   */
  @GET
  @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML})
  public Response getStyles(@Context OgcApi api, @Context ApiRequestContext requestContext) {
    QueriesHandlerStyles.QueryInputStyles queryInput =
        new ImmutableQueryInputStyles.Builder().from(getGenericQueryInput(api.getData())).build();

    return queryHandler.handle(QueriesHandlerStyles.Query.STYLES, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
