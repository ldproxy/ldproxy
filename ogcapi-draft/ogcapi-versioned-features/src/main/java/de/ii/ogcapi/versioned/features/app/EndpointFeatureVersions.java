/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.PermissionGroup;
import de.ii.ogcapi.foundation.domain.PermissionGroup.Base;
import de.ii.ogcapi.versioned.features.domain.ImmutableQueryInputTimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMapFormatExtension;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesQueriesHandler;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.features.domain.SchemaBase;
import io.dropwizard.auth.Auth;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @title Time Map
 * @path collections/{collectionId}/items/{featureId}/versions
 * @langEn Returns the Time Map of a feature — a JSON document listing every version of the feature
 *     as a {@code memento} link with its RFC 7089 {@code datetime} link attribute.
 * @langDe Liefert die Time Map eines Features — ein JSON-Dokument, das jede Version des Features
 *     als {@code memento}-Link mit einem RFC-7089-{@code datetime}-Linkattribut auflistet.
 */
@Singleton
@AutoBind
public class EndpointFeatureVersions extends EndpointSubCollection {

  private static final List<String> TAGS = ImmutableList.of("Access data");
  private static final String OP_ID = "getFeatureVersions";

  private static final PermissionGroup GROUP_READ =
      PermissionGroup.of(Base.READ, "data", "access and query features");

  private final FeaturesCoreProviders providers;
  private final VersionedFeaturesQueriesHandler queryHandler;

  @Inject
  public EndpointFeatureVersions(
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      VersionedFeaturesQueriesHandler queryHandler) {
    super(extensionRegistry);
    this.providers = providers;
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData.getCollections().values().stream()
        .anyMatch(c -> isEnabledForApi(apiData, c.getId()));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && apiData
            .getCollectionData(collectionId)
            .flatMap(
                collectionData ->
                    providers
                        .getQueryablesSchema(apiData, collectionData)
                        .flatMap(SchemaBase::getPrimaryInterval))
            .isPresent();
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder builder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_VERSIONS);

    String path = "/collections/{collectionId}/items/{featureId}/versions";
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(p -> "collectionId".equals(p.getName())).findAny();
    if (optCollectionIdParam.isEmpty()) {
      return builder.build();
    }
    OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
    boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
    List<String> collectionIds =
        explode ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");

    Map<MediaType, ApiMediaTypeContent> responseContent =
        getResourceFormats().stream()
            .map(f -> (TimeMapFormatExtension) f)
            .collect(
                Collectors.toMap(
                    f -> f.getMediaType().type(),
                    TimeMapFormatExtension::getContent,
                    (a, b) -> a,
                    LinkedHashMap::new));
    List<ApiHeader> headers = ImmutableList.of();

    for (String collectionId : collectionIds) {
      if (!collectionId.startsWith("{") && !isEnabledForApi(apiData, collectionId)) {
        continue;
      }
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path, collectionId);
      String resourcePath = path.replace("{collectionId}", collectionId);
      ImmutableOgcApiResourceData.Builder resourceBuilder =
          new ImmutableOgcApiResourceData.Builder()
              .path(resourcePath)
              .pathParameters(pathParameters);
      ApiOperation.getResource(
              apiData,
              resourcePath,
              false,
              queryParameters,
              headers,
              responseContent,
              "retrieve the Time Map of a feature in feature collection '" + collectionId + "'",
              Optional.of(
                  "Lists every version of the feature as a memento link with an RFC 7089 datetime"
                      + " link attribute, plus self / original / latest-version links."),
              Optional.empty(),
              getOperationId(OP_ID, collectionId),
              GROUP_READ,
              TAGS,
              Optional.empty(),
              Optional.empty())
          .ifPresent(op -> resourceBuilder.putOperations(HttpMethods.GET.name(), op));
      builder.putResources(resourcePath, resourceBuilder.build());
    }
    return builder.build();
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    return extensionRegistry.getExtensionsForType(TimeMapFormatExtension.class);
  }

  @GET
  @Path("/{collectionId}/items/{featureId}/versions")
  public Response getVersions(
      @Auth Optional<User> optionalUser,
      @Context ApiRequestContext requestContext,
      @PathParam("collectionId") String collectionId,
      @PathParam("featureId") String featureId) {
    OgcApi api = requestContext.getApi();
    checkCollectionExists(api.getData(), collectionId);

    FeatureTypeConfigurationOgcApi collectionData =
        api.getData().getCollections().get(collectionId);
    if (collectionData == null || !isEnabledForApi(api.getData(), collectionId)) {
      throw new NotFoundException(
          "Versioned Features is not enabled for collection '" + collectionId + "'.");
    }

    ImmutableQueryInputTimeMap queryInput =
        new ImmutableQueryInputTimeMap.Builder()
            .collectionId(collectionId)
            .featureId(featureId)
            .featureProvider(providers.getFeatureProviderOrThrow(api.getData(), collectionData))
            .build();

    return queryHandler.handle(
        VersionedFeaturesQueriesHandler.Query.TIME_MAP, queryInput, requestContext);
  }
}
