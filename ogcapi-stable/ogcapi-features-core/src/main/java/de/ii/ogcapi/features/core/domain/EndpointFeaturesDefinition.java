/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.GROUP_DATA_READ;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.features.core.app.FeaturesCoreBuildingBlock;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiOperationLink;
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
import de.ii.ogcapi.foundation.domain.ParameterExtension;
import de.ii.ogcapi.foundation.domain.RuntimeQueryParametersExtension;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.SchemaBase;
import jakarta.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class EndpointFeaturesDefinition extends EndpointSubCollection {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointFeaturesDefinition.class);
  private static final List<String> TAGS = ImmutableList.of("Access data");
  public static final String OP_ID_GET_ITEM = "getItem";
  public static final String OP_ID_GET_ITEMS = "getItems";
  public static final String OP_ID_CREATE_ITEM = "createItem";
  public static final String OP_ID_REPLACE_ITEM = "replaceItem";
  public static final String OP_ID_UPDATE_ITEM = "updateItem";
  public static final String OP_ID_DELETE_ITEM = "deleteItem";

  protected final FeaturesCoreProviders providers;

  public EndpointFeaturesDefinition(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry);
    this.providers = providers;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder()
            .from(super.onStartup(api, apiValidation))
            .mode(apiValidation);

    OgcApiDataV2 apiData = api.getData();
    if (apiValidation != MODE.NONE) {
      apiData
          .getCollections()
          .forEach(
              (collectionId, collectionData) -> {
                extensionRegistry.getExtensionsForType(FeatureFormatExtension.class).stream()
                    .filter(f -> f.isEnabledForApi(apiData, collectionData.getId()))
                    .filter(
                        f ->
                            !f.supportsEmbedding()
                                && providers
                                    .getFeatureSchema(apiData, collectionData)
                                    .filter(SchemaBase::hasEmbeddedFeature)
                                    .isPresent())
                    .forEach(
                        f ->
                            builder.addStrictErrors(
                                "In API '{}', collection '{}': Feature format '{}' does not support embedding, but the feature type has embedded features. Requesting the format will result in an error.",
                                api.getId(),
                                collectionId,
                                f.getMediaType().label()));
              });
    }

    return builder.build();
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(FeatureFormatExtension.class);
    return formats;
  }

  public Map<MediaType, ApiMediaTypeContent> getFeatureContent(
      List<? extends FormatExtension> formats,
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      boolean featureCollection) {
    return formats.stream()
        .filter(f -> f instanceof FeatureFormatExtension)
        .map(f -> (FeatureFormatExtension) f)
        .filter(
            f ->
                collectionId
                    .map(s -> f.isEnabledForApi(apiData, s))
                    .orElseGet(() -> f.isEnabledForApi(apiData)))
        .map(f -> f.getFeatureContent(apiData, collectionId, featureCollection))
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(c -> c.getOgcApiMediaType().type(), c -> c));
  }

  public void generateDefinition(
      OgcApiDataV2 apiData,
      ImmutableApiEndpointDefinition.Builder definitionBuilder,
      String subSubPath,
      String summary,
      String description,
      String logPrefix) {

    String path = "/collections/{collectionId}" + subSubPath;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> "collectionId".equals(param.getName())).findAny();

    if (optCollectionIdParam.isEmpty()) {
      LOGGER.error(
          "Path parameter 'collectionId' is missing for resource at path '{}'. The resource will not be available.",
          path);
      return;
    }

    final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
    final boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);

    if (explode) {
      for (String collectionId : collectionIdParam.getValues(apiData)) {
        List<OgcApiQueryParameter> queryParameters =
            Stream.concat(
                    extensionRegistry.getExtensionsForType(OgcApiQueryParameter.class).stream()
                        .filter(
                            param ->
                                param.isApplicable(apiData, path, collectionId, HttpMethods.GET))
                        .sorted(Comparator.comparing(ParameterExtension::getName)),
                    extensionRegistry
                        .getExtensionsForType(RuntimeQueryParametersExtension.class)
                        .stream()
                        .map(
                            extension ->
                                extension.getRuntimeParameters(
                                    apiData, Optional.of(collectionId), path, HttpMethods.GET))
                        .flatMap(Collection::stream))
                .collect(Collectors.toUnmodifiableList());

        List<ApiHeader> headers =
            getHeaders(extensionRegistry, apiData, path, collectionId, HttpMethods.GET);

        generateCollectionDefinition(
            apiData,
            definitionBuilder,
            subSubPath,
            path,
            pathParameters,
            queryParameters,
            headers,
            collectionId,
            summary,
            description,
            logPrefix);

        // since the generation is quite expensive, check if the startup was interrupted
        // after every collection
        if (Thread.interrupted()) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    } else {
      Optional<String> representativeCollectionId = getRepresentativeCollectionId(apiData);
      List<OgcApiQueryParameter> queryParameters;
      List<ApiHeader> headers = getHeaders(extensionRegistry, apiData, path, null, HttpMethods.GET);

      if (representativeCollectionId.isPresent()) {
        String collectionId = representativeCollectionId.get();
        queryParameters =
            Stream.concat(
                    extensionRegistry.getExtensionsForType(OgcApiQueryParameter.class).stream()
                        .filter(
                            param ->
                                param.isApplicable(apiData, path, collectionId, HttpMethods.GET))
                        .sorted(Comparator.comparing(ParameterExtension::getName)),
                    extensionRegistry
                        .getExtensionsForType(RuntimeQueryParametersExtension.class)
                        .stream()
                        .map(
                            extension ->
                                extension.getRuntimeParameters(
                                    apiData, Optional.of(collectionId), path, HttpMethods.GET))
                        .flatMap(Collection::stream))
                .collect(Collectors.toUnmodifiableList());
      } else {
        queryParameters =
            extensionRegistry.getExtensionsForType(OgcApiQueryParameter.class).stream()
                .filter(param -> param.isApplicable(apiData, path, HttpMethods.GET))
                .sorted(Comparator.comparing(ParameterExtension::getName))
                .collect(Collectors.toUnmodifiableList());
      }

      generateCollectionDefinition(
          apiData,
          definitionBuilder,
          subSubPath,
          path,
          pathParameters,
          queryParameters,
          headers,
          "{collectionId}",
          summary,
          description,
          logPrefix);
    }
  }

  private void generateCollectionDefinition(
      OgcApiDataV2 apiData,
      ImmutableApiEndpointDefinition.Builder definitionBuilder,
      String subSubPath,
      String path,
      List<OgcApiPathParameter> pathParameters,
      List<OgcApiQueryParameter> queryParameters,
      List<ApiHeader> headers,
      String collectionId,
      String summary,
      String description,
      String logPrefix) {

    final String operationSummary = summary + collectionId + "'";
    final Optional<String> operationDescription = Optional.of(description);
    String resourcePath = "/collections/" + collectionId + subSubPath;
    ImmutableOgcApiResourceData.Builder resourceBuilder =
        new ImmutableOgcApiResourceData.Builder().path(resourcePath).pathParameters(pathParameters);

    Map<MediaType, ApiMediaTypeContent> responseContent =
        getFeatureContent(
            getResourceFormats(),
            apiData,
            collectionId.startsWith("{") ? Optional.empty() : Optional.of(collectionId),
            "/collections/{collectionId}/items".equals(path));
    ApiOperation.getResource(
            apiData,
            resourcePath,
            false,
            queryParameters,
            headers,
            responseContent,
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId(
                subSubPath.contains("{featureId}") ? OP_ID_GET_ITEM : OP_ID_GET_ITEMS,
                collectionId),
            GROUP_DATA_READ,
            TAGS,
            FeaturesCoreBuildingBlock.MATURITY,
            FeaturesCoreBuildingBlock.SPEC)
        .map(
            operation ->
                subSubPath.contains("{featureId}")
                    ? withLinkFromItems(
                        withMissingFeatureStatusCodes(apiData, collectionId, operation),
                        collectionId)
                    : operation)
        .ifPresent(operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));

    definitionBuilder.putResources(resourcePath, resourceBuilder.build());
  }

  private static ApiOperation withLinkFromItems(ApiOperation operation, String collectionId) {
    return new ImmutableApiOperation.Builder()
        .from(operation)
        .addLinkedFrom(linkFromItems("item", collectionId))
        .build();
  }

  /**
   * @param name the name of the link
   * @param collectionId the id of the collection or {@code {collectionId}}, if the collections are
   *     not listed in the API definition
   * @return a link from the features of the collection to an operation on a single feature, the
   *     feature is the first feature of the response
   */
  public static ApiOperationLink linkFromItems(String name, String collectionId) {
    return getLinkFrom(
        name,
        collectionId,
        OP_ID_GET_ITEMS,
        Map.of("featureId", "$response.body#/features/0/id"),
        "Uses the id of the first feature in the response. A link selects a single value, it does not iterate over the list.");
  }

  // a missing feature may be reported with another status code than 404, e.g. 410 Gone
  private ApiOperation withMissingFeatureStatusCodes(
      OgcApiDataV2 apiData, String collectionId, ApiOperation operation) {
    Set<Integer> statusCodes =
        extensionRegistry.getExtensionsForType(SingleFeatureMissingHandler.class).stream()
            .filter(
                handler ->
                    collectionId.startsWith("{")
                        ? handler.isEnabledForApi(apiData)
                        : handler.isEnabledForApi(apiData, collectionId))
            .flatMap(handler -> handler.getStatusCodes().stream())
            .collect(Collectors.toUnmodifiableSet());
    return statusCodes.isEmpty()
        ? operation
        : new ImmutableApiOperation.Builder()
            .from(operation)
            .addAllErrorStatusCodes(statusCodes)
            .build();
  }
}
