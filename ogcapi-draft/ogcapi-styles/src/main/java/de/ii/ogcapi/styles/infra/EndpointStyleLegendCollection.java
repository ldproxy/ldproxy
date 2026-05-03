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
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.styles.app.StylesBuildingBlock;
import de.ii.ogcapi.styles.domain.ImmutableQueryInputStyle;
import de.ii.ogcapi.styles.domain.QueriesHandlerStyles;
import de.ii.ogcapi.styles.domain.StyleLegendFormatExtension;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Collection Style Legend
 * @path collections/{collectionId}/styles/{styleId}/legend
 * @langEn This endpoint retrieves the legend for a map style. The legend is provided as a PNG file,
 *     which can be used to understand the symbols and colors used in the map style.
 * @langDe Dieser Endpunkt ruft die Legende für einen Map Style ab. Die Legende wird als PNG-Datei
 *     bereitgestellt, die zum Verständnis der im Map Style verwendeten Symbole und Farben verwendet
 *     werden kann.
 * @ref:formats {@link de.ii.ogcapi.styles.domain.StyleLegendFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointStyleLegendCollection extends EndpointSubCollection
    implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointStyleLegendCollection.class);

  protected static final List<String> TAGS = ImmutableList.of("Discover and fetch styles");

  private final QueriesHandlerStyles queryHandler;

  @Inject
  public EndpointStyleLegendCollection(
      ExtensionRegistry extensionRegistry, QueriesHandlerStyles queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(StyleLegendFormatExtension.class);
    return formats;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(StylesConfiguration.class)
        .map(c -> c.isEnabled() && Objects.equals(c.getLegendEnabled(), Boolean.TRUE))
        .orElse(false);
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_STYLE_LEGEND_COLLECTION);
    final String subSubPath = "/styles/{styleId}/legend";
    final String path = "/collections/{collectionId}" + subSubPath;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    if (pathParameters.stream().noneMatch(param -> param.getName().equals("styleId"))) {
      LOGGER.error(
          "Path parameter 'styleId' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      final Optional<OgcApiPathParameter> optCollectionIdParam =
          pathParameters.stream().filter(param -> param.getName().equals("collectionId")).findAny();
      if (optCollectionIdParam.isEmpty()) {
        LOGGER.error(
            "Path parameter 'collectionId' missing for resource at path '"
                + path
                + "'. The GET method will not be available.");
      } else {
        final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
        boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
        final List<String> collectionIds =
            (explode) ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
        for (String collectionId : collectionIds) {
          List<OgcApiQueryParameter> queryParameters =
              getQueryParameters(extensionRegistry, apiData, path, collectionId);
          String operationSummary =
              "fetch the legend of the style `{styleId}` for collection `" + collectionId + "`";
          Optional<String> operationDescription =
              Optional.of(
                  "This endpoint retrieves the legend for a map style. "
                      + "The legend is provided as a PNG file, "
                      + "which can be used to understand the "
                      + "symbols and colors used in the map style.");
          String resourcePath = path.replace("{collectionId}", collectionId);
          ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
              new ImmutableOgcApiResourceAuxiliary.Builder()
                  .path(resourcePath)
                  .pathParameters(pathParameters);
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
                  getOperationId("getStyleLegend", collectionId),
                  GROUP_STYLES_READ,
                  TAGS,
                  StylesBuildingBlock.MATURITY,
                  StylesBuildingBlock.SPEC)
              .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
          definitionBuilder.putResources(path, resourceBuilder.build());
        }
      }
    }

    return definitionBuilder.build();
  }

  /**
   * Fetch the legend of a style
   *
   * @param collectionId the local identifier of the collection
   * @param styleId the local identifier of a specific style
   * @return the style in a json file
   */
  @Path("/{collectionId}/styles/{styleId}/legend")
  @GET
  @Produces("image/png")
  public Response getStyleLegend(
      @PathParam("collectionId") String collectionId,
      @PathParam("styleId") String styleId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    OgcApiDataV2 apiData = api.getData();
    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles/{styleId}/legend",
        "collectionId",
        collectionId);
    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles/{styleId}/legend",
        "styleId",
        styleId);
    checkCollectionExists(apiData, collectionId);

    QueriesHandlerStyles.QueryInputStyle queryInput =
        new ImmutableQueryInputStyle.Builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .styleId(styleId)
            .build();

    return queryHandler.handle(QueriesHandlerStyles.Query.STYLE_LEGEND, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
