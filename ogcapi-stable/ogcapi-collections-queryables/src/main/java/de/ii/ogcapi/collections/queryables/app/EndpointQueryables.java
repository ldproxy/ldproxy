/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.queryables.app;

import static de.ii.ogcapi.common.domain.QueriesHandlerCommon.GROUP_COLLECTIONS_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
import de.ii.ogcapi.features.core.domain.QueriesHandlerSchema;
import de.ii.ogcapi.features.core.domain.QueriesHandlerSchema.Query;
import de.ii.ogcapi.features.core.domain.SchemaFormatExtension;
import de.ii.ogcapi.features.core.domain.SchemaType;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
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
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.values.domain.ValueStore;
import io.dropwizard.auth.Auth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Queryables
 * @path collections/{collectionId}/queryables
 * @langEn The Queryables resource identifies and describes the properties that can be referenced in
 *     filter expressions to select specific features that meet the criteria identified in the
 *     filter. The response is a JSON Schema document that describes a single JSON object where each
 *     property is a queryable.
 * @langDe Die Ressource Queryables identifiziert und beschreibt die Eigenschaften, auf die in
 *     Filterausdrücken verwiesen werden kann. Die Antwort ist ein JSON-Schema-Dokument, das ein
 *     einzelnes JSON-Objekt beschreibt, bei dem jede Eigenschaft eine abfragbare Eigenschaft ist.
 * @ref:formats {@link de.ii.ogcapi.features.core.domain.SchemaFormatExtension}
 */
@Singleton
@AutoBind
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class EndpointQueryables extends EndpointSubCollection
    implements ConformanceClass, ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointQueryables.class);

  private static final List<String> TAGS = ImmutableList.of("Discover data collections");

  private final QueriesHandlerSchema queryHandler;
  private final FeaturesCoreProviders providers;
  private final JsonSchemaCache schemaCache;

  @Inject
  public EndpointQueryables(
      ExtensionRegistry extensionRegistry,
      QueriesHandlerSchema queryHandler,
      ValueStore valueStore,
      FeaturesCoreProviders featuresCoreProviders) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.providers = featuresCoreProviders;
    this.schemaCache =
        new SchemaCacheQueryables(valueStore.forType(Codelist.class)::asMap, featuresCoreProviders);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(
        "http://www.opengis.net/spec/ogcapi-features-3/1.0/conf/queryables",
        "http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/schemas",
        "http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/queryables");
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData.getCollections().keySet().stream()
        .anyMatch(collectionId -> isEnabledForApi(apiData, collectionId));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getExtension(QueryablesConfiguration.class, collectionId)
        .filter(QueryablesConfiguration::isEnabled)
        .filter(QueryablesConfiguration::endpointIsEnabled)
        .isPresent();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return QueryablesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(SchemaFormatExtension.class);
    }
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    final ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_QUERYABLES);
    final String subSubPath = "/queryables";
    final String path = "/collections/{collectionId}" + subSubPath;
    final List<OgcApiPathParameter> pathParameters =
        getPathParameters(extensionRegistry, apiData, path);
    final Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> "collectionId".equals(param.getName())).findAny();
    if (optCollectionIdParam.isEmpty()) {
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
        final List<OgcApiQueryParameter> queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId);
        final String operationSummary =
            "retrieve the queryables of the feature collection '" + collectionId + "'";
        final Optional<String> operationDescription =
            Optional.of(
                "The Queryables resources identifies the properties that can be "
                    + "referenced in filter expressions to select specific features that meet the criteria identified in the filter. "
                    + "The response is a JSON Schema document that describes a single JSON object where each property is a queryable.\n\n"
                    + "Note: The queryables schema does not specify a schema of any object that can be retrieved from the API.\n\n"
                    + "The descriptive metadata (title and description of the property) as well as the schema information (data type and "
                    + "constraints like a list of allowed values or minimum/maxmimum values are provided to support clients to construct"
                    + "meaningful queries for the data.");
        final String resourcePath = "/collections/" + collectionId + subSubPath;
        final ImmutableOgcApiResourceData.Builder resourceBuilder =
            new ImmutableOgcApiResourceData.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters);
        final Map<MediaType, ApiMediaTypeContent> responseContent = getResponseContent(apiData);
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
                getOperationId("getQueryables", collectionId),
                GROUP_COLLECTIONS_READ,
                TAGS,
                QueryablesBuildingBlock.MATURITY,
                QueryablesBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    }

    return definitionBuilder.build();
  }

  @GET
  @Path("/{collectionId}/queryables")
  @Produces({"application/schema+json", "text/html"})
  public Response getQueryables(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context UriInfo uriInfo,
      @PathParam("collectionId") String collectionId) {

    String definitionPath = "/collections/{collectionId}/queryables";
    checkPathParameter(
        extensionRegistry, api.getData(), definitionPath, "collectionId", collectionId);

    QueryablesConfiguration configuration =
        api.getData()
            .getExtension(QueryablesConfiguration.class, collectionId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Queryables configuration not found for collection: " + collectionId));

    List<Profile> defaultProfiles =
        extensionRegistry.getExtensionsForType(Profile.class).stream()
            .filter(
                profile ->
                    configuration.getDefaultProfiles().containsKey(profile.getProfileSet())
                        && profile
                            .getId()
                            .equals(
                                configuration.getDefaultProfiles().get(profile.getProfileSet())))
            .toList();

    final QueriesHandlerSchema.QueryInputSchema queryInput =
        new ImmutableQueryInputSchema.Builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .type(SchemaType.QUERYABLES)
            .schemaCache(schemaCache)
            .defaultProfilesResource(defaultProfiles)
            .build();

    return queryHandler.handle(Query.SCHEMA, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(providers.getFeatureProviderOrThrow(apiData));
  }
}
