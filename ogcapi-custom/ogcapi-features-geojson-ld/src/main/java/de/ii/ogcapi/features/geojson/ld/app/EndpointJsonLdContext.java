/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.ld.app;

import static de.ii.ogcapi.common.domain.QueriesHandlerCommon.GROUP_COLLECTIONS_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.features.geojson.ld.domain.GeoJsonLdConfiguration;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
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
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class EndpointJsonLdContext extends EndpointSubCollection {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointJsonLdContext.class);
  private static final List<String> TAGS = ImmutableList.of("Discover data collections");

  private final ResourceStore contextStore;

  @Inject
  EndpointJsonLdContext(ResourceStore blobStore, ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
    this.contextStore = blobStore.with(GeoJsonLdBuildingBlock.STORE_RESOURCE_TYPE);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GeoJsonLdConfiguration.class;
  }

  private java.nio.file.Path getContextPath(
      String apiId, String collectionId, String contextFileName, String extension) {
    return Objects.nonNull(contextFileName)
        ? java.nio.file.Path.of(apiId).resolve(contextFileName)
        : java.nio.file.Path.of(apiId).resolve(collectionId + "." + extension);
  }

  @Path("/{collectionId}/context")
  @GET
  @Produces("application/ld+json")
  public Response getContext(
      @Context ApiRequestContext apiRequestContext,
      @Context OgcApi api,
      @PathParam("collectionId") String collectionId)
      throws IOException {

    ContextFormatExtension format =
        extensionRegistry.getExtensionsForType(ContextFormatExtension.class).stream()
            .filter(f -> f.isEnabledForApi(api.getData(), collectionId))
            .filter(f -> f.getMediaType().matches(apiRequestContext.getMediaType().type()))
            .findFirst()
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            apiRequestContext.getMediaType())));

    Optional<String> contextFileName =
        api.getData()
            .getCollections()
            .get(collectionId)
            .getExtension(GeoJsonLdConfiguration.class)
            .map(cfg -> cfg.getContextFileName());
    java.nio.file.Path contextPath =
        getContextPath(
            api.getId(),
            collectionId,
            contextFileName.orElse(null),
            format.getMediaType().parameter());

    Optional<InputStream> context = contextStore.content(contextPath);

    if (context.isEmpty()) {
      throw new NotFoundException(
          String.format("The %s context was not found.", format.getMediaType().label()));
    }

    // TODO validate, that it is a valid JSON-LD Context document

    return Response.ok(context.get(), "application/ld+json").build();
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(ContextFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_FEATURES_JSONLD_CONTEXT);
    String subSubPath = "/context";
    String path = "/collections/{collectionId}" + subSubPath;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> "collectionId".equals(param.getName())).findAny();
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
        if (explode
            && !apiData
                .getCollections()
                .get(collectionId)
                .getExtension(GeoJsonLdConfiguration.class)
                .map(ExtensionConfiguration::isEnabled)
                .orElse(false))
          // skip, if disabled for the collection
          continue;
        final List<OgcApiQueryParameter> queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId);
        final String operationSummary =
            "retrieve the JSON-LD context for the feature collection '" + collectionId + "'";
        Optional<String> operationDescription = Optional.empty();
        String resourcePath = "/collections/" + collectionId + subSubPath;
        ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
            new ImmutableOgcApiResourceAuxiliary.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters);
        ApiOperation.getResource(
                apiData,
                resourcePath,
                false,
                queryParameters,
                ImmutableList.of(),
                getResponseContent(apiData),
                operationSummary,
                operationDescription,
                Optional.empty(),
                getOperationId("getJsonLdContext", collectionId),
                GROUP_COLLECTIONS_READ,
                TAGS,
                GeoJsonLdBuildingBlock.MATURITY,
                GeoJsonLdBuildingBlock.SPEC)
            .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    }

    return definitionBuilder.build();
  }
}
