/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.infra;

import static de.ii.ogcapi.resources.domain.QueriesHandlerResources.GROUP_RESOURCES_WRITE;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.resources.app.ResourcesBuildingBlock;
import de.ii.ogcapi.resources.domain.QueriesHandlerResources;
import de.ii.ogcapi.resources.domain.ResourceFormatExtension;
import de.ii.ogcapi.resources.domain.ResourcesConfiguration;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.blobs.domain.Blob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.web.domain.LastModified;
import io.dropwizard.auth.Auth;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Resource
 * @path resources/{resourceId}
 * @langEn Create, update or delete a file resource.
 * @langDe Erzeugen, Aktualisieren oder Löschen einer Dateiressource.
 * @ref:formats {@link de.ii.ogcapi.resources.domain.ResourceFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointResourcesManager extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointResourcesManager.class);
  private static final List<String> TAGS =
      ImmutableList.of("Create, update and delete other resources");

  private final ResourceStore resourcesStore;
  private final QueriesHandlerResources queriesHandlerResources;

  @Inject
  public EndpointResourcesManager(
      ResourceStore blobStore,
      ExtensionRegistry extensionRegistry,
      QueriesHandlerResources queriesHandlerResources) {
    super(extensionRegistry);

    this.resourcesStore = blobStore.with(ResourcesBuildingBlock.STORE_RESOURCE_TYPE);
    this.queriesHandlerResources = queriesHandlerResources;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(ResourcesConfiguration.class)
        .map(ResourcesConfiguration::isManagerEnabled)
        .orElse(false);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ResourcesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats =
          extensionRegistry.getExtensionsForType(ResourceFormatExtension.class).stream()
              .filter(ResourceFormatExtension::canSupportTransactions)
              .collect(Collectors.toList());
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("resources")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_RESOURCES_MANAGER);
    String path = "/resources/{resourceId}";
    HttpMethods methodReplace = HttpMethods.PUT;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path, methodReplace);
    String operationSummary = "replace a file resource or add a new one";
    Optional<String> operationDescription =
        Optional.of(
            "Replace an existing resource with the id `resourceId`. If no "
                + "such resource exists, a new resource with that id is added. "
                + "A sprite used in a Mapbox Style stylesheet consists of "
                + "three resources. Each of the resources needs to be created "
                + "(and eventually deleted) separately.\n"
                + "The PNG bitmap image (resourceId ends in '.png'), the JSON "
                + "index file (resourceId of the same name, but ends in '.json' "
                + "instead of '.png') and the PNG  bitmap image for "
                + "high-resolution displays (the file ends in '.@2x.png').\n"
                + "The resource will only by available in the native format in "
                + "which the resource is posted. There is no support for "
                + "automated conversions to other representations.");
    ImmutableOgcApiResourceData.Builder resourceBuilder =
        new ImmutableOgcApiResourceData.Builder().path(path).pathParameters(pathParameters);
    Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(apiData);
    ApiOperation.of(
            path,
            methodReplace,
            requestContent,
            queryParameters,
            ImmutableList.of(),
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("createOrReplaceResource"),
            GROUP_RESOURCES_WRITE,
            TAGS,
            ResourcesBuildingBlock.MATURITY,
            ResourcesBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(methodReplace.name(), operation));
    HttpMethods methodDelete = HttpMethods.DELETE;
    queryParameters = getQueryParameters(extensionRegistry, apiData, path, methodDelete);
    operationSummary = "delete a file resource";
    operationDescription =
        Optional.of(
            "Delete an existing resource with the id `resourceId`. If no "
                + "such resource exists, an error is returned.");
    ApiOperation.of(
            path,
            methodDelete,
            ImmutableMap.of(),
            queryParameters,
            ImmutableList.of(),
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("deleteResource"),
            GROUP_RESOURCES_WRITE,
            TAGS,
            ResourcesBuildingBlock.MATURITY,
            ResourcesBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(methodDelete.name(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  /**
   * create or update a resource
   *
   * @param resourceId the local identifier of a specific style
   * @return empty response (204)
   */
  @Path("/{resourceId}")
  @PUT
  @Consumes(MediaType.WILDCARD)
  public Response putResource(
      @Auth Optional<User> optionalUser,
      @PathParam("resourceId") String resourceId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpServletRequest request,
      byte[] requestBody)
      throws IOException {

    final String apiId = api.getId();
    final java.nio.file.Path resourcePath = java.nio.file.Path.of(apiId).resolve(resourceId);

    try {
      Optional<Blob> resourceBlob = resourcesStore.get(resourcePath);

      if (resourceBlob.isEmpty()) {
        throw new NotFoundException(
            MessageFormat.format("The resource ''{0}'' does not exist.", resourceId));
      }

      Blob blob = resourceBlob.get();
      Date lastModified = LastModified.from(blob.lastModified());
      EntityTag eTag = blob.eTag();
      Response.ResponseBuilder response =
          queriesHandlerResources.evaluatePreconditions(requestContext, lastModified, eTag);

      if (Objects.nonNull(response)) return response.build();

    } catch (IOException e) {
      throw new ServerErrorException("resource could not be read: " + resourceId, 500);
    }

    return getResourceFormats().stream()
        .filter(format -> requestContext.getMediaType().matches(format.getMediaType().type()))
        .findAny()
        .map(ResourceFormatExtension.class::cast)
        .orElseThrow(
            () ->
                new NotSupportedException(
                    MessageFormat.format(
                        "The provided media type ''{0}'' is not supported for this resource.",
                        requestContext.getMediaType())))
        .putResource(requestBody, resourceId, api.getData(), requestContext);
  }

  /**
   * deletes a resource
   *
   * @param resourceId the local identifier of a specific style
   * @return empty response (204)
   */
  @Path("/{resourceId}")
  @DELETE
  public Response deleteResource(
      @Auth Optional<User> optionalUser,
      @PathParam("resourceId") String resourceId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context OgcApi dataset) {

    final String apiId = api.getId();
    final java.nio.file.Path resourcePath = java.nio.file.Path.of(apiId).resolve(resourceId);

    try {
      Optional<Blob> resourceBlob = resourcesStore.get(resourcePath);

      if (resourceBlob.isEmpty()) {
        throw new NotFoundException(
            MessageFormat.format("The resource ''{0}'' does not exist.", resourceId));
      }

      Blob blob = resourceBlob.get();
      Date lastModified = LastModified.from(blob.lastModified());
      EntityTag eTag = blob.eTag();
      Response.ResponseBuilder response =
          queriesHandlerResources.evaluatePreconditions(requestContext, lastModified, eTag);

      if (Objects.nonNull(response)) return response.build();

    } catch (IOException e) {
      throw new ServerErrorException("resource could not be read: " + resourceId, 500);
    }

    try {
      resourcesStore.delete(java.nio.file.Path.of(dataset.getId(), resourceId));
    } catch (IOException e) {
      // ignore
    }

    return Response.noContent().build();
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(resourcesStore);
  }
}
