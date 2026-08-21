/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.GROUP_DATA_WRITE;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.crs.domain.HeaderContentCrs;
import de.ii.ogcapi.crud.app.CommandHandlerCrud.QueryInputFeatureCreate;
import de.ii.ogcapi.crud.app.CommandHandlerCrud.QueryInputFeatureDelete;
import de.ii.ogcapi.crud.app.CommandHandlerCrud.QueryInputFeatureReplace;
import de.ii.ogcapi.crud.domain.CrudConfiguration;
import de.ii.ogcapi.features.core.domain.EndpointFeaturesDefinition;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.OptionalCapability;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import io.dropwizard.auth.Auth;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Features, Feature
 * @path collections/{collectionId}/items, collections/{collectionId}/items/{featureId}
 * @langEn Create, Replace, Update and Delete features.
 * @langDe Erzeugen, Ersetzen, Aktualisieren und Löschen von Features.
 */
@Singleton
@AutoBind
public class EndpointCrud extends EndpointSubCollection
    implements ConformanceClass, ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointCrud.class);
  private static final List<String> TAGS = ImmutableList.of("Mutate data");
  private static final MediaType GML_MEDIA_TYPE = new MediaType("application", "gml+xml");
  // "Precondition Required" (RFC 6585, 3), there is no constant for the status code in JAX-RS
  private static final int STATUS_PRECONDITION_REQUIRED = 428;
  // The draft is co-branded, the conformance classes that are not specific to features are
  // identified by the URIs of OGC API - Common - Part 5.
  private static final String CONF_CLASS_PREFIX =
      "http://www.opengis.net/spec/ogcapi-common-5/1.0/conf/";

  private final FeaturesCoreProviders providers;
  private final CommandHandlerCrud commandHandler;
  private final CrsInfo crsInfo;
  private final CrsSupport crsSupport;
  private final FeaturesQuery queryParser;
  private List<Profile> crudProfiles;

  @Inject
  public EndpointCrud(
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      CommandHandlerCrud commandHandler,
      CrsInfo crsInfo,
      CrsSupport crsSupport,
      FeaturesQuery queryParser) {
    super(extensionRegistry);
    this.providers = providers;
    this.commandHandler = commandHandler;
    this.crsInfo = crsInfo;
    this.crsSupport = crsSupport;
    this.queryParser = queryParser;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    this.crudProfiles = Profile.of(extensionRegistry, "all-as-receivable");

    return super.onStartup(api, apiValidation);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrudConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && providers
            .getFeatureProvider(apiData)
            .map(FeatureProvider::mutations)
            .filter(OptionalCapability::isSupported)
            .isPresent();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && providers
            .getFeatureProvider(apiData, apiData.getCollections().get(collectionId))
            .map(FeatureProvider::mutations)
            .filter(OptionalCapability::isSupported)
            .isPresent();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    ImmutableList.Builder<String> builder =
        new ImmutableList.Builder<String>()
            .add(
                CONF_CLASS_PREFIX + "create-replace-delete",
                CONF_CLASS_PREFIX + "update",
                // the conformance class "Features" is specific to OGC API - Features
                "http://www.opengis.net/spec/ogcapi-features-4/1.0/conf/features");

    if (apiData.getCollections().values().stream()
        .anyMatch(
            cd ->
                cd.getExtension(CrudConfiguration.class)
                    .map(CrudConfiguration::supportsLastModified)
                    .orElse(false))) {
      builder.add(CONF_CLASS_PREFIX + "optimistic-locking-timestamps");
    }

    if (apiData.getCollections().keySet().stream()
        .anyMatch(collectionId -> canValidate(apiData, collectionId))) {
      builder.add(CONF_CLASS_PREFIX + "handling");
    }

    return builder.build();
  }

  // The request body of a mutation request is validated for "Prefer: handling=strict", if any of
  // the formats that are supported in mutation requests can validate a request body.
  private boolean canValidate(OgcApiDataV2 apiData, String collectionId) {
    return isEnabledForApi(apiData, collectionId)
        && transactionFormats().stream().anyMatch(f -> f.canValidate(apiData, collectionId));
  }

  private boolean canValidate(OgcApiDataV2 apiData, String collectionId, MediaType contentType) {
    return transactionFormat(contentType)
        .filter(format -> format.canValidate(apiData, collectionId))
        .isPresent();
  }

  private List<FeatureFormatExtension> transactionFormats() {
    return extensionRegistry.getExtensionsForType(FeatureFormatExtension.class).stream()
        .filter(FeatureFormatExtension::canSupportTransactions)
        .collect(Collectors.toList());
  }

  private Optional<FeatureFormatExtension> transactionFormat(MediaType contentType) {
    return transactionFormats().stream()
        .filter(format -> format.getMediaType().type().isCompatible(contentType))
        .findFirst();
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats =
          extensionRegistry.getExtensionsForType(FeatureFormatExtension.class).stream()
              .filter(FeatureFormatExtension::canSupportTransactions)
              .collect(Collectors.toList());
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_FEATURES_TRANSACTION);

    computeDefinitionItems(apiData, definitionBuilder);
    computeDefinitionItem(apiData, definitionBuilder);

    return definitionBuilder.build();
  }

  private void computeDefinitionItems(
      OgcApiDataV2 apiData, ImmutableApiEndpointDefinition.Builder builder) {
    String subSubPath = "/items";
    String path = "/collections/{collectionId}" + subSubPath;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> "collectionId".equals(param.getName())).findAny();

    if (!optCollectionIdParam.isPresent()) {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The resource will not be available.");
      return;
    }

    final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
    final boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
    final List<String> collectionIds =
        (explode) ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
    for (String collectionId : collectionIds) {
      // POST should only be available if the collection supports auto-generated ids
      if (!hasGeneratedId(apiData, collectionId)) {
        continue;
      }

      final List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path, collectionId, HttpMethods.POST);
      final List<ApiHeader> headers =
          getHeaders(extensionRegistry, apiData, path, collectionId, HttpMethods.POST);
      final String operationSummary =
          "add a feature in the feature collection '" + collectionId + "'";
      Optional<String> operationDescription =
          Optional.of(
              "The content of the request is a new feature in one of the supported encodings. The URI of the new feature is returned in the header `Location`.");
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
              getOperationId(EndpointFeaturesDefinition.OP_ID_CREATE_ITEM, collectionId),
              GROUP_DATA_WRITE,
              TAGS,
              CrudBuildingBlock.MATURITY,
              CrudBuildingBlock.SPEC,
              false)
          .ifPresent(
              operation -> resourceBuilder.putOperations(HttpMethods.POST.name(), operation));
      builder.putResources(resourcePath, resourceBuilder.build());
    }
  }

  private void computeDefinitionItem(
      OgcApiDataV2 apiData, ImmutableApiEndpointDefinition.Builder builder) {
    String subSubPath = "/items/{featureId}";
    String path = "/collections/{collectionId}" + subSubPath;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> "collectionId".equals(param.getName())).findAny();

    if (!optCollectionIdParam.isPresent()) {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The resource will not be available.");
      return;
    }

    final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
    final boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
    final List<String> collectionIds =
        explode ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
    for (String collectionId : collectionIds) {
      boolean hasGeneratedId = hasGeneratedId(apiData, collectionId);
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path, collectionId, HttpMethods.PUT);
      List<ApiHeader> headers =
          getHeaders(extensionRegistry, apiData, path, collectionId, HttpMethods.PUT);
      String operationSummary =
          "%supdate a feature in the feature collection '%s'"
              .formatted(hasGeneratedId ? "" : "add or ", collectionId);
      Optional<String> operationDescription =
          Optional.of(
              "The content of the request is a feature in one of the supported encodings. The id of the %supdated feature is `{featureId}`."
                  .formatted(hasGeneratedId ? "" : "new or "));
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
              getOperationId(EndpointFeaturesDefinition.OP_ID_REPLACE_ITEM, collectionId),
              GROUP_DATA_WRITE,
              TAGS,
              CrudBuildingBlock.MATURITY,
              CrudBuildingBlock.SPEC,
              !hasGeneratedId)
          .ifPresent(operation -> resourceBuilder.putOperations(HttpMethods.PUT.name(), operation));

      queryParameters =
          getQueryParameters(extensionRegistry, apiData, path, collectionId, HttpMethods.PATCH);
      headers = getHeaders(extensionRegistry, apiData, path, collectionId, HttpMethods.PATCH);
      operationSummary = "update a feature in the feature collection '" + collectionId + "'";
      operationDescription =
          Optional.of(
              "The content of the request is a JSON merge patch document (RFC 7396). The id of the updated feature is `{featureId}`.");
      Map<MediaType, ApiMediaTypeContent> patchRequestContent = new LinkedHashMap<>();
      patchRequestContent.put(
          FeatureFormatJsonMergePatch.MEDIA_TYPE.type(),
          FeatureFormatJsonMergePatch.MEDIA_TYPE_CONTENT);
      for (Map.Entry<MediaType, ApiMediaTypeContent> entry :
          getRequestContent(apiData).entrySet()) {
        if (!GML_MEDIA_TYPE.equals(entry.getKey())) {
          patchRequestContent.put(entry.getKey(), entry.getValue());
        }
      }
      requestContent = patchRequestContent;

      ApiOperation.of(
              resourcePath,
              HttpMethods.PATCH,
              requestContent,
              queryParameters,
              headers,
              operationSummary,
              operationDescription,
              Optional.empty(),
              getOperationId(EndpointFeaturesDefinition.OP_ID_UPDATE_ITEM, collectionId),
              GROUP_DATA_WRITE,
              TAGS,
              CrudBuildingBlock.MATURITY,
              CrudBuildingBlock.SPEC,
              false)
          .ifPresent(
              operation -> resourceBuilder.putOperations(HttpMethods.PATCH.name(), operation));

      queryParameters =
          getQueryParameters(extensionRegistry, apiData, path, collectionId, HttpMethods.DELETE);
      headers = getHeaders(extensionRegistry, apiData, path, collectionId, HttpMethods.DELETE);
      operationSummary = "delete a feature in the feature collection '" + collectionId + "'";
      operationDescription = Optional.of("The feature with id `{featureId}` will be deleted.");

      ApiOperation.of(
              resourcePath,
              HttpMethods.DELETE,
              ImmutableMap.of(),
              queryParameters,
              headers,
              operationSummary,
              operationDescription,
              Optional.empty(),
              getOperationId(EndpointFeaturesDefinition.OP_ID_DELETE_ITEM, collectionId),
              GROUP_DATA_WRITE,
              TAGS,
              CrudBuildingBlock.MATURITY,
              CrudBuildingBlock.SPEC,
              false)
          .ifPresent(
              operation -> resourceBuilder.putOperations(HttpMethods.DELETE.name(), operation));

      builder.putResources(resourcePath, resourceBuilder.build());
    }
  }

  private boolean hasGeneratedId(OgcApiDataV2 apiData, String collectionId) {
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    return providers
        .getFeatureProviderOrThrow(apiData, collectionData)
        .info()
        .hasGeneratedId(providers.getFeatureSchema(apiData, collectionData).get().getName());
  }

  @Path("/{collectionId}/items")
  @POST
  @Consumes({"application/geo+json", "application/gml+xml"})
  public Response postItems(
      @Auth Optional<User> optionalUser,
      @PathParam("collectionId") String collectionId,
      @HeaderParam("Content-Crs") String crs,
      @HeaderParam("Prefer") List<String> prefer,
      @HeaderParam("Link") List<String> links,
      @Context OgcApi api,
      @Context ApiRequestContext apiRequestContext,
      @Context HttpServletRequest request,
      InputStream requestBody) {

    FeatureTypeConfigurationOgcApi collectionData =
        api.getData().getCollections().get(collectionId);

    FeatureProvider featureProvider =
        providers.getFeatureProviderOrThrow(api.getData(), collectionData);

    FeaturesCoreConfiguration coreConfiguration =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .filter(
                cfg ->
                    cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                        != FeaturesCoreConfiguration.ItemType.unknown)
            .orElseThrow(() -> new NotFoundException("Features are not supported for this API."));

    String featureType = coreConfiguration.getFeatureType().orElse(collectionId);

    // rejects a CRS that is not supported for the collection (the geometries in the body are
    // checked separately, they may declare a CRS of their own)
    EpsgCrs contentCrs =
        HeaderContentCrs.parse(crs, api.getData(), Optional.of(collectionData), crsSupport);

    MediaType contentType = requiredContentType(request);

    final boolean validate = validateRequestBody(api.getData(), collectionId, contentType, prefer);

    QueryInputFeatureCreate queryInput =
        ImmutableQueryInputFeatureCreate.builder()
            .collectionId(collectionId)
            .featureType(featureType)
            .crs(contentCrs)
            .featureProvider(featureProvider)
            .requestBody(requestBody)
            .contentType(contentType)
            .validate(validate)
            .linkHeaders(links)
            .build();

    try {
      return HeaderPrefer.withAppliedHandling(
          commandHandler.postItemsResponse(queryInput, apiRequestContext), prefer);
    } catch (IllegalArgumentException e) {
      throw validate ? rejectedRequestBody(e, prefer) : e;
    }
  }

  @Path("/{collectionId}/items/{featureId}")
  @PUT
  @Consumes({"application/geo+json", "application/gml+xml"})
  public Response putItem(
      @Auth Optional<User> optionalUser,
      @PathParam("collectionId") String collectionId,
      @PathParam("featureId") final String featureId,
      @HeaderParam("Content-Crs") String crs,
      @HeaderParam("Prefer") List<String> prefer,
      @HeaderParam("Link") List<String> links,
      @HeaderParam("If-Match") String ifMatch,
      @HeaderParam("If-Unmodified-Since") String ifUnmodifiedSince,
      @Context OgcApi api,
      @Context ApiRequestContext apiRequestContext,
      @Context HttpServletRequest request,
      InputStream requestBody) {

    FeatureTypeConfigurationOgcApi collectionData =
        api.getData().getCollections().get(collectionId);

    Optional<CrudConfiguration> crudConfiguration =
        collectionData.getExtension(CrudConfiguration.class);
    checkPreconditionHeaders(crudConfiguration, ifMatch, ifUnmodifiedSince);

    FeatureProvider featureProvider =
        providers.getFeatureProviderOrThrow(api.getData(), collectionData);

    FeaturesCoreConfiguration coreConfiguration =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .filter(
                cfg ->
                    cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                        != FeaturesCoreConfiguration.ItemType.unknown)
            .orElseThrow(() -> new NotFoundException("Features are not supported for this API."));

    String featureType = coreConfiguration.getFeatureType().orElse(collectionId);

    // rejects a CRS that is not supported for the collection (the geometries in the body are
    // checked separately, they may declare a CRS of their own)
    EpsgCrs contentCrs =
        HeaderContentCrs.parse(crs, api.getData(), Optional.of(collectionData), crsSupport);

    MediaType contentType = requiredContentType(request);

    QueryParameterSet queryParameterSet = getQueryParameterSet(api, collectionData, contentCrs);
    FeatureQuery query =
        queryParser.requestToFeatureQuery(
            api.getData(),
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            queryParameterSet,
            featureId,
            Optional.empty(),
            SchemaBase.Scope.RECEIVABLE,
            false);

    final boolean validate = validateRequestBody(api.getData(), collectionId, contentType, prefer);

    QueryInputFeatureReplace queryInput =
        ImmutableQueryInputFeatureReplace.builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .featureType(featureType)
            .crs(contentCrs)
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .featureId(featureId)
            .query(query)
            .lastModifiedQuery(
                lastModifiedQuery(
                    api,
                    collectionData,
                    coreConfiguration,
                    queryParameterSet,
                    featureId,
                    crudConfiguration))
            .queryParameterSet(queryParameterSet)
            .featureProvider(featureProvider)
            .requestBody(requestBody)
            .contentType(contentType)
            .validate(validate)
            .linkHeaders(links)
            .profiles(crudProfiles)
            .isAllowCreate(!hasGeneratedId(api.getData(), collectionId))
            .build();

    try {
      return HeaderPrefer.withAppliedHandling(
          commandHandler.putItemResponse(queryInput, apiRequestContext), prefer);
    } catch (IllegalArgumentException e) {
      throw validate ? rejectedRequestBody(e, prefer) : e;
    }
  }

  @Path("/{collectionId}/items/{featureId}")
  @PATCH
  @Consumes({"application/merge-patch+json", "application/geo+json"})
  public Response patchItem(
      @Auth Optional<User> optionalUser,
      @PathParam("collectionId") String collectionId,
      @PathParam("featureId") final String featureId,
      @HeaderParam("Content-Crs") String crs,
      @HeaderParam("Link") List<String> links,
      @HeaderParam("If-Match") String ifMatch,
      @HeaderParam("If-Unmodified-Since") String ifUnmodifiedSince,
      @Context OgcApi api,
      @Context ApiRequestContext apiRequestContext,
      @Context HttpServletRequest request,
      InputStream requestBody) {

    FeatureTypeConfigurationOgcApi collectionData =
        api.getData().getCollections().get(collectionId);

    Optional<CrudConfiguration> crudConfiguration =
        collectionData.getExtension(CrudConfiguration.class);
    checkPreconditionHeaders(crudConfiguration, ifMatch, ifUnmodifiedSince);

    FeatureProvider featureProvider =
        providers.getFeatureProviderOrThrow(api.getData(), collectionData);

    FeaturesCoreConfiguration coreConfiguration =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .filter(
                cfg ->
                    cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                        != FeaturesCoreConfiguration.ItemType.unknown)
            .orElseThrow(() -> new NotFoundException("Features are not supported for this API."));

    String featureType = coreConfiguration.getFeatureType().orElse(collectionId);

    // rejects a CRS that is not supported for the collection (the geometries in the body are
    // checked separately, they may declare a CRS of their own)
    EpsgCrs contentCrs =
        HeaderContentCrs.parse(crs, api.getData(), Optional.of(collectionData), crsSupport);

    QueryParameterSet queryParameterSet = getQueryParameterSet(api, collectionData, contentCrs);
    FeatureQuery query =
        queryParser.requestToFeatureQuery(
            api.getData(),
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            queryParameterSet,
            featureId,
            Optional.empty(),
            SchemaBase.Scope.RECEIVABLE,
            false);

    MediaType contentType = requiredContentType(request);

    QueryInputFeatureReplace queryInput =
        ImmutableQueryInputFeatureReplace.builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .featureType(featureType)
            .featureId(featureId)
            .crs(contentCrs)
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .query(query)
            .lastModifiedQuery(
                lastModifiedQuery(
                    api,
                    collectionData,
                    coreConfiguration,
                    queryParameterSet,
                    featureId,
                    crudConfiguration))
            .queryParameterSet(queryParameterSet)
            .featureProvider(featureProvider)
            .requestBody(requestBody)
            .contentType(contentType)
            .validate(false)
            .linkHeaders(links)
            .profiles(crudProfiles)
            .isAllowCreate(false)
            .build();

    return commandHandler.patchItemResponse(queryInput, apiRequestContext);
  }

  @Path("/{collectionId}/items/{featureId}")
  @DELETE
  public Response deleteItem(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext apiRequestContext,
      @PathParam("collectionId") String collectionId,
      @PathParam("featureId") final String featureId,
      @HeaderParam("If-Match") String ifMatch,
      @HeaderParam("If-Unmodified-Since") String ifUnmodifiedSince) {

    FeatureTypeConfigurationOgcApi collectionData =
        api.getData().getCollections().get(collectionId);

    Optional<CrudConfiguration> crudConfiguration =
        collectionData.getExtension(CrudConfiguration.class);
    checkPreconditionHeaders(crudConfiguration, ifMatch, ifUnmodifiedSince);

    FeatureProvider featureProvider =
        providers.getFeatureProviderOrThrow(
            api.getData(), api.getData().getCollections().get(collectionId));

    FeaturesCoreConfiguration coreConfiguration =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .filter(
                cfg ->
                    cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                        != FeaturesCoreConfiguration.ItemType.unknown)
            .orElseThrow(() -> new NotFoundException("Features are not supported for this API."));

    QueryParameterSet queryParameterSet = getQueryParameterSet(api, collectionData, null);
    FeatureQuery query =
        queryParser.requestToFeatureQuery(
            api.getData(),
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            queryParameterSet,
            featureId,
            Optional.empty(),
            SchemaBase.Scope.RECEIVABLE,
            false);

    QueryInputFeatureDelete queryInput =
        ImmutableQueryInputFeatureDelete.builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .featureType(coreConfiguration.getFeatureType().orElse(collectionId))
            .featureId(featureId)
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .query(query)
            .lastModifiedQuery(
                lastModifiedQuery(
                    api,
                    collectionData,
                    coreConfiguration,
                    queryParameterSet,
                    featureId,
                    crudConfiguration))
            .queryParameterSet(queryParameterSet)
            .featureProvider(featureProvider)
            .profiles(crudProfiles)
            .build();

    return commandHandler.deleteItemResponse(queryInput, apiRequestContext);
  }

  private QueryParameterSet getQueryParameterSet(
      OgcApi api, FeatureTypeConfigurationOgcApi collectionData, EpsgCrs crs) {
    List<OgcApiQueryParameter> parameterDefinitions =
        getQueryParameters(
            extensionRegistry,
            api.getData(),
            "/collections/{collectionId}/items/{featureId}",
            collectionData.getId(),
            HttpMethods.GET);
    Map<String, String> values =
        Objects.nonNull(crs)
            ? ImmutableMap.of("schema", "receivables", "crs", crs.toUriString())
            : ImmutableMap.of("schema", "receivables");
    return QueryParameterSet.of(parameterDefinitions, values)
        .evaluate(api, Optional.of(collectionData));
  }

  // Evaluates the preconditions that can be evaluated from the headers alone, in the order of
  // RFC 9110, 13.2.2.
  private static void checkPreconditionHeaders(
      Optional<CrudConfiguration> crudConfiguration, String ifMatch, String ifUnmodifiedSince) {
    // No entity tag is known for a feature in a request that changes it, so no entity tag in an
    // 'If-Match' header can match and the precondition cannot be met (RFC 9110, 13.1.1). "*" is
    // met, if the feature exists, which is checked while processing the request.
    if (Objects.nonNull(ifMatch) && !"*".equals(ifMatch.trim())) {
      throw new ClientErrorException(
          "The precondition in the 'If-Match' header cannot be met, entity tags are not supported in requests that change a feature. Use an 'If-Unmodified-Since' header.",
          Status.PRECONDITION_FAILED);
    }

    if (crudConfiguration.map(CrudConfiguration::supportsLastModified).orElse(false)
        && Objects.isNull(ifUnmodifiedSince)) {
      throw new ClientErrorException(
          "Requests to change a feature for this collection must include an 'If-Unmodified-Since' header.",
          Response.status(STATUS_PRECONDITION_REQUIRED, "Precondition Required").build());
    }
  }

  private static MediaType requiredContentType(HttpServletRequest request) {
    String contentType = request.getContentType();
    if (Objects.isNull(contentType) || contentType.isBlank()) {
      throw new BadRequestException(
          "Requests with a request body must include a 'Content-Type' header.");
    }
    return mediaTypeFromString(contentType);
  }

  private boolean validateRequestBody(
      OgcApiDataV2 apiData, String collectionId, MediaType contentType, List<String> prefer) {
    return HeaderPrefer.parseHandling(prefer, HeaderPrefer.Handling.LENIENT)
            == HeaderPrefer.Handling.STRICT
        && canValidate(apiData, collectionId, contentType);
  }

  // The strict handling preference has been applied, so the rejection of the request body reports
  // the preference, too (RFC 7240, 3).
  private static RuntimeException rejectedRequestBody(
      IllegalArgumentException e, List<String> prefer) {
    String message =
        Objects.nonNull(e.getCause()) && Objects.nonNull(e.getCause().getMessage())
            ? String.format("%s: %s", e.getMessage(), e.getCause().getMessage())
            : e.getMessage();
    return new ClientErrorException(
        message,
        HeaderPrefer.withAppliedHandling(Response.status(Status.BAD_REQUEST).build(), prefer));
  }

  // The last modification time of a feature is often not part of the representation that is used in
  // mutation requests (a server-managed timestamp is not a receivable property), so the internal
  // request for the current feature cannot always determine it. This query requests the returnable
  // representation, which includes the property.
  private Optional<FeatureQuery> lastModifiedQuery(
      OgcApi api,
      FeatureTypeConfigurationOgcApi collectionData,
      FeaturesCoreConfiguration coreConfiguration,
      QueryParameterSet queryParameterSet,
      String featureId,
      Optional<CrudConfiguration> crudConfiguration) {
    if (!crudConfiguration.map(CrudConfiguration::supportsLastModified).orElse(false)) {
      return Optional.empty();
    }

    return Optional.of(
        queryParser.requestToFeatureQuery(
            api.getData(),
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            queryParameterSet,
            featureId,
            Optional.empty(),
            SchemaBase.Scope.RETURNABLE,
            false));
  }

  private ImmutableFeatureQuery.Builder processCoordinatePrecision(
      ImmutableFeatureQuery.Builder queryBuilder, Map<String, Integer> coordinatePrecision) {
    // check, if we need to add a precision value; for this we need the target CRS,
    // so we need to build the query to get the CRS
    ImmutableFeatureQuery query = queryBuilder.build();
    if (!coordinatePrecision.isEmpty() && query.getCrs().isPresent()) {
      List<Integer> precisionList =
          crsInfo.getPrecisionList(query.getCrs().get(), coordinatePrecision);
      if (!precisionList.isEmpty()) {
        queryBuilder.geometryPrecision(precisionList);
      }
    }
    return queryBuilder;
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(
        commandHandler, crsInfo, queryParser, providers.getFeatureProviderOrThrow(apiData));
  }
}
