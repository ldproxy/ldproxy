/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.infra.manager;

import static de.ii.ogcapi.styles.domain.QueriesHandlerStyles.GROUP_STYLES_WRITE;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.common.domain.QueryParameterDryRun;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.styles.app.StylesBuildingBlock;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.ogcapi.styles.domain.manager.ImmutableQueryInputStyleCreateReplace;
import de.ii.ogcapi.styles.domain.manager.ImmutableQueryInputStyleDelete;
import de.ii.ogcapi.styles.domain.manager.QueriesHandlerStylesManager;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import io.dropwizard.auth.Auth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Collection Styles, Collection Style
 * @path collection/{collectionId}/styles, collection/{collectionId}/styles/{styleId}
 * @langEn Create, Replace and Delete styles of a feature collection.
 * @langDe Erzeugen, Ersetzen und Löschen von Styles einer Feature Collection.
 * @ref:formats {@link de.ii.ogcapi.styles.domain.StyleFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointStylesManagerCollection extends EndpointSubCollection
    implements ConformanceClass, ApiExtensionHealth {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EndpointStylesManagerCollection.class);
  private static final List<String> TAGS = ImmutableList.of("Create, update and delete styles");

  private final QueriesHandlerStylesManager queryHandler;

  @Inject
  public EndpointStylesManagerCollection(
      ExtensionRegistry extensionRegistry, QueriesHandlerStylesManager queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-styles-1/0.0/conf/manage-styles");
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    Optional<StylesConfiguration> extension =
        apiData.getCollections().get(collectionId).getExtension(StylesConfiguration.class);

    return extension
        .filter(StylesConfiguration::isEnabled)
        .filter(StylesConfiguration::isManagerEnabled)
        .isPresent();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats =
          extensionRegistry.getExtensionsForType(StyleFormatExtension.class).stream()
              .collect(Collectors.toList());
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    Optional<StylesConfiguration> stylesExtension = apiData.getExtension(StylesConfiguration.class);
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_STYLES_MANAGER_COLLECTION);
    String subSubPath = "/styles";
    String path = "/collections/{collectionId}" + subSubPath;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> param.getName().equals("collectionId")).findAny();
    if (!optCollectionIdParam.isPresent()) {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The resource will not be available.");
    } else {
      final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
      final boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
      final List<String> collectionIds =
          (explode) ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
      for (String collectionId : collectionIds) {
        final List<OgcApiQueryParameter> queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId, HttpMethods.POST);
        final List<ApiHeader> headers =
            getHeaders(extensionRegistry, apiData, path, collectionId, HttpMethods.POST);
        final String operationSummary =
            "add a new style in the feature collection '" + collectionId + "'";
        String description = "Adds a style to this style collection";
        if (stylesExtension.map(StylesConfiguration::isValidationEnabled).orElse(false)) {
          description +=
              " or just validates a style.\n"
                  + "If the header `Prefer` is set to `handling=strict`, the style will be validated before adding "
                  + "the style to the server. If the parameter `dry-run` is set to `true`, the server will "
                  + "not be changed and only any validation errors will be reported";
        }
        description += ".\n" + "If a new style is created, the following rules apply:\n";
        if (stylesExtension.map(StylesConfiguration::shouldUseIdFromStylesheet).orElse(false)) {
          description +=
              "* If the style submitted in the request body includes an identifier (this depends on "
                  + "the style encoding), that identifier will be used. If a style with that identifier "
                  + "already exists, an error is returned.\n"
                  + "* If no identifier can be determined from the submitted style, the server will assign "
                  + "a new identifier to the style.\n";
        } else {
          description += "* The server will assign a new identifier to the style.\n";
        }
        description +=
            "* A minimal style metadata resource is created at `/styles/{styleId}/metadata`. "
                + "You can update the metadata using a PUT request to keep the style metadata consistent with "
                + "the style definition.\n"
                + "* The URI of the new style is returned in the header `Location`.\n";
        Optional<String> operationDescription = Optional.of(description);
        String resourcePath = "/collections/" + collectionId + subSubPath;
        ImmutableOgcApiResourceData.Builder resourceBuilder =
            new ImmutableOgcApiResourceData.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters);
        Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(apiData);
        ApiOperation.of(
                resourcePath,
                HttpMethods.POST,
                requestContent,
                queryParameters,
                headers,
                operationSummary,
                operationDescription,
                Optional.empty(),
                getOperationId("createStyle", collectionId),
                GROUP_STYLES_WRITE,
                TAGS,
                StylesBuildingBlock.MATURITY,
                StylesBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.POST.name(), operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    }
    subSubPath = "/styles/{styleId}";
    path = "/collections/{collectionId}" + subSubPath;
    pathParameters = getPathParameters(extensionRegistry, apiData, path);
    optCollectionIdParam =
        pathParameters.stream().filter(param -> param.getName().equals("collectionId")).findAny();
    if (!optCollectionIdParam.isPresent()) {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The resource will not be available.");
    } else {
      final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
      final boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
      final List<String> collectionIds =
          explode ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
      for (String collectionId : collectionIds) {
        List<OgcApiQueryParameter> queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId, HttpMethods.PUT);
        List<ApiHeader> headers =
            getHeaders(extensionRegistry, apiData, path, collectionId, HttpMethods.PUT);
        String operationSummary =
            "replace a stylesheet in the feature collection '" + collectionId + "'";
        String description = "Replace an existing style with the id `styleId` ";
        if (stylesExtension.map(StylesConfiguration::isValidationEnabled).orElse(false)) {
          description +=
              " or just validate a style.\n"
                  + "If the header `Prefer` is set to `handling=strict`, the style will be validated before adding "
                  + "the style to the server. If the parameter `dry-run` is set to `true`, the server will "
                  + "not be changed and only any validation errors will be reported";
        }
        description +=
            ".\nThe style metadata resource at `/styles/{styleId}/metadata` " + "is not updated.";
        Optional<String> operationDescription = Optional.of(description);
        String resourcePath = "/collections/" + collectionId + subSubPath;
        ImmutableOgcApiResourceData.Builder resourceBuilder =
            new ImmutableOgcApiResourceData.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters);
        Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(apiData);
        ApiOperation.of(
                resourcePath,
                HttpMethods.PUT,
                requestContent,
                queryParameters,
                headers,
                operationSummary,
                operationDescription,
                Optional.empty(),
                getOperationId("replaceStyle", collectionId),
                GROUP_STYLES_WRITE,
                TAGS,
                StylesBuildingBlock.MATURITY,
                StylesBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.PUT.name(), operation));

        queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId, HttpMethods.DELETE);
        headers = getHeaders(extensionRegistry, apiData, path, collectionId, HttpMethods.DELETE);
        operationSummary = "delete a style in the feature collection '" + collectionId + "'";
        operationDescription =
            Optional.of(
                "Delete the style with the id `styleId`. "
                    + "Deleting a style also deletes the subordinate resources, i.e., the style metadata.");
        ApiOperation.of(
                resourcePath,
                HttpMethods.DELETE,
                ImmutableMap.of(),
                queryParameters,
                headers,
                operationSummary,
                operationDescription,
                Optional.empty(),
                getOperationId("deleteStyle", collectionId),
                GROUP_STYLES_WRITE,
                TAGS,
                StylesBuildingBlock.MATURITY,
                StylesBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.DELETE.name(), operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    }
    return definitionBuilder.build();
  }

  private static void checkHeader(
      Optional<StylesConfiguration> stylesConfiguration, String ifMatch, String ifUnmodifiedSince) {
    if (stylesConfiguration.map(StylesConfiguration::supportsEtag).orElse(false)
        && Objects.isNull(ifMatch)) {
      throw new BadRequestException(
          "Requests to change a style for this collection must include an 'If-Match' header.");
    } else if (stylesConfiguration.map(StylesConfiguration::supportsLastModified).orElse(false)
        && Objects.isNull(ifUnmodifiedSince)) {
      throw new BadRequestException(
          "Requests to change a style for this collection must include an 'If-Unmodified-Since' header.");
    }
  }

  /**
   * creates a new style
   *
   * @return empty response (201), with Location header
   */
  @Path("/{collectionId}/styles")
  @POST
  public Response postStyle(
      @Auth Optional<User> optionalUser,
      @PathParam("collectionId") String collectionId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpServletRequest request,
      byte[] requestBody) {

    OgcApiDataV2 apiData = api.getData();

    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles",
        "collectionId",
        collectionId);

    ImmutableQueryInputStyleCreateReplace.Builder builder =
        new ImmutableQueryInputStyleCreateReplace.Builder()
            .collectionId(collectionId)
            .contentType(mediaTypeFromString(request.getContentType()))
            .requestBody(requestBody)
            .strict(strictHandling(request.getHeaders("Prefer")));

    applyParameters(requestContext.getQueryParameterSet(), builder);

    return queryHandler.handle(
        QueriesHandlerStylesManager.Query.CREATE_STYLE, builder.build(), requestContext);
  }

  /**
   * creates or updates a style(sheet)
   *
   * @param styleId the local identifier of a specific style
   * @return empty response (204)
   */
  @Path("/{collectionId}/styles/{styleId}")
  @PUT
  public Response putStyle(
      @Auth Optional<User> optionalUser,
      @PathParam("collectionId") String collectionId,
      @PathParam("styleId") String styleId,
      @HeaderParam("If-Match") String ifMatch,
      @HeaderParam("If-Unmodified-Since") String ifUnmodifiedSince,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpServletRequest request,
      byte[] requestBody) {

    OgcApiDataV2 apiData = api.getData();

    Optional<StylesConfiguration> StylesConfiguration =
        api.getData().getExtension(StylesConfiguration.class);
    checkHeader(StylesConfiguration, ifMatch, ifUnmodifiedSince);

    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles/{styleId}",
        "collectionId",
        collectionId);
    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles/{styleId}",
        "styleId",
        styleId);

    ImmutableQueryInputStyleCreateReplace.Builder builder =
        new ImmutableQueryInputStyleCreateReplace.Builder()
            .collectionId(collectionId)
            .styleId(styleId)
            .contentType(mediaTypeFromString(request.getContentType()))
            .requestBody(requestBody)
            .strict(strictHandling(request.getHeaders("Prefer")));

    applyParameters(requestContext.getQueryParameterSet(), builder);

    return queryHandler.handle(
        QueriesHandlerStylesManager.Query.REPLACE_STYLE, builder.build(), requestContext);
  }

  /**
   * deletes a style
   *
   * @param styleId the local identifier of a specific style
   * @return empty response (204)
   */
  @Path("/{collectionId}/styles/{styleId}")
  @DELETE
  public Response deleteStyle(
      @Auth Optional<User> optionalUser,
      @PathParam("collectionId") String collectionId,
      @PathParam("styleId") String styleId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    OgcApiDataV2 apiData = api.getData();
    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles/{styleId}",
        "collectionId",
        collectionId);
    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles/{styleId}",
        "styleId",
        styleId);

    QueriesHandlerStylesManager.QueryInputStyleDelete queryInput =
        new ImmutableQueryInputStyleDelete.Builder()
            .collectionId(collectionId)
            .styleId(styleId)
            .build();

    return queryHandler.handle(
        QueriesHandlerStylesManager.Query.DELETE_STYLE, queryInput, requestContext);
  }

  private static void applyParameters(
      QueryParameterSet queryParameterSet, ImmutableQueryInputStyleCreateReplace.Builder builder) {
    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof QueryParameterDryRun) {
        ((QueryParameterDryRun) parameter).applyTo(builder, queryParameterSet);
      }
    }
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
