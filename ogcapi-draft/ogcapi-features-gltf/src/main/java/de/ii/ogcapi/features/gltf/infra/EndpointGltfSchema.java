/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.infra;

import static de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsQueriesHandler.GROUP_TILES_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.features.gltf.app.SchemaFormat3dMetadataJson;
import de.ii.ogcapi.features.gltf.domain.GltfConfiguration;
import de.ii.ogcapi.features.gltf.domain.ImmutableQueryInputGltfSchema;
import de.ii.ogcapi.features.gltf.domain.QueriesHandlerGltf;
import de.ii.ogcapi.features.gltf.domain.QueriesHandlerGltf.Query;
import de.ii.ogcapi.features.gltf.domain.QueriesHandlerGltf.QueryInputGltfSchema;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableApiOperation;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import io.dropwizard.auth.Auth;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title glTF Schema
 * @path collections/{collectionId}/gltf/schema
 * @langEn The glTF Schema resource describes the feature properties and the enumerations as encoded
 *     in the glTF models for the feature collection. See the [3D Metadata
 *     Specification](https://github.com/CesiumGS/3d-tiles/tree/main/specification/Metadata#schema)
 *     for details.
 * @langDe Die glTF-Schema-Ressource beschreibt die Feature-Eigenschaften und die Aufzählungen, wie
 *     sie in den glTF-Modellen für die Feature Collection kodiert sind. Einzelheiten finden Sie in
 *     der [3D Metadata Specification]
 *     (https://github.com/CesiumGS/3d-tiles/tree/main/specification/Metadata#schema).
 * @ref:formats {@link de.ii.ogcapi.features.gltf.domain.SchemaFormat3dMetadata}
 */
@Singleton
@AutoBind
public class EndpointGltfSchema extends EndpointSubCollection implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointGltfSchema.class);

  private static final List<String> TAGS = ImmutableList.of("Access data as 3D Tiles");

  private final QueriesHandlerGltf queryHandler;

  @Inject
  public EndpointGltfSchema(ExtensionRegistry extensionRegistry, QueriesHandlerGltf queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GltfConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(SchemaFormat3dMetadataJson.class);
    }
    return formats;
  }

  @Override
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_3D_TILES);
    String subSubPath = "/gltf/schema";
    String path = "/collections/{collectionId}" + subSubPath;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> "collectionId".equals(param.getName())).findAny();
    if (optCollectionIdParam.isPresent()) {
      final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
      final boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
      final List<String> collectionIds =
          explode ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
      for (String collectionId : collectionIds) {
        List<OgcApiQueryParameter> queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId);
        String operationSummary =
            "retrieve the 3D Metadata schema of the feature collection '" + collectionId + "'";
        Optional<String> operationDescription =
            Optional.of(
                "The glTF Schema resource describes the feature properties and the "
                    + "enumerations as encoded in the glTF models for the feature collection. "
                    + "See the [3D Metadata Specification](https://github.com/CesiumGS/3d-tiles/tree/main/specification/Metadata#schema) "
                    + "for details.");
        String resourcePath = "/collections/" + collectionId + subSubPath;
        final ImmutableOgcApiResourceData.Builder resourceBuilder =
            new ImmutableOgcApiResourceData.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters);
        Map<MediaType, ApiMediaTypeContent> responseContent = getResponseContent(apiData);
        ApiOperation.getResource(
                apiData,
                resourcePath,
                false,
                queryParameters,
                ImmutableList.of(),
                responseContent,
                operationSummary,
                operationDescription,
                Optional.empty(),
                getOperationId("getGltfSchema", collectionId),
                GROUP_TILES_READ,
                TAGS,
                Optional.of(SpecificationMaturity.DRAFT_LDPROXY),
                Optional.empty())
            .ifPresent(
                operation ->
                    resourceBuilder.putOperations(
                        HttpMethods.GET.name(),
                        // Cesium sends all query parameters of the correct URI also to the schema
                        // request
                        new ImmutableApiOperation.Builder()
                            .from(operation)
                            .ignoreUnknownQueryParameters(true)
                            .build()));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    } else {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The resource will not be available.");
    }

    return definitionBuilder.build();
  }

  @GET
  @Path("/{collectionId}/gltf/schema")
  public Response getGltfSchema(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context UriInfo uriInfo,
      @PathParam("collectionId") String collectionId) {

    QueryInputGltfSchema queryInput =
        ImmutableQueryInputGltfSchema.builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .build();

    return queryHandler.handle(Query.SCHEMA, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
