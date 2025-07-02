/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.infra;

import static de.ii.ogcapi.common.domain.QueriesHandlerCommon.GROUP_COLLECTIONS_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.collections.schema.app.QueryParameterProfileSchema;
import de.ii.ogcapi.collections.schema.app.SchemaBuildingBlock;
import de.ii.ogcapi.collections.schema.app.SchemaCacheFeatures;
import de.ii.ogcapi.collections.schema.domain.SchemaConfiguration;
import de.ii.ogcapi.features.core.domain.CollectionPropertiesFormat;
import de.ii.ogcapi.features.core.domain.CollectionPropertiesQueriesHandler;
import de.ii.ogcapi.features.core.domain.CollectionPropertiesQueriesHandler.Query;
import de.ii.ogcapi.features.core.domain.CollectionPropertiesQueriesHandler.QueryInputCollectionProperties;
import de.ii.ogcapi.features.core.domain.CollectionPropertiesType;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputCollectionProperties;
import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
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
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.values.domain.ValueStore;
import io.dropwizard.auth.Auth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * @title Feature Schema
 * @path collections/{collectionId}/schema
 * @langEn JSON Schema of the features of the collection `collectionId`.
 * @langDe JSON Schema der Features der Collection `collectionId`.
 * @ref:formats {@link de.ii.ogcapi.features.core.domain.CollectionPropertiesFormat}
 */
@Singleton
@AutoBind
public class EndpointSchema extends EndpointSubCollection
    implements ApiExtensionHealth, ConformanceClass {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointSchema.class);

  private static final List<String> TAGS = ImmutableList.of("Discover data collections");

  private final CollectionPropertiesQueriesHandler queryHandler;
  private final ValueStore valueStore;
  private final JsonSchemaCache schemaCache;

  @Inject
  public EndpointSchema(
      ExtensionRegistry extensionRegistry,
      CollectionPropertiesQueriesHandler queryHandler,
      ValueStore valueStore) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.valueStore = valueStore;
    this.schemaCache = new SchemaCacheFeatures(valueStore.forType(Codelist.class)::asMap);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of(
        "http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/schemas",
        "http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/core-roles-features",
        "http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/feature-references",
        "http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/returnables-and-receivables");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SchemaConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(CollectionPropertiesFormat.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_SCHEMA);
    String subSubPath = "/schema";
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
            getQueryParameters(extensionRegistry, apiData, path, collectionId);
        final String operationSummary =
            "retrieve the schema of features in the feature collection '" + collectionId + "'";
        Optional<String> operationDescription = Optional.empty(); // TODO
        String resourcePath = "/collections/" + collectionId + subSubPath;
        ImmutableOgcApiResourceData.Builder resourceBuilder =
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
                getOperationId("getSchema", collectionId),
                GROUP_COLLECTIONS_READ,
                TAGS,
                SchemaBuildingBlock.MATURITY,
                SchemaBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    }

    return definitionBuilder.build();
  }

  @GET
  @Path("/{collectionId}/schema")
  @Produces({"application/schema+json", "text/html"})
  public Response getSchema(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context UriInfo uriInfo,
      @PathParam("collectionId") String collectionId) {

    String definitionPath = "/collections/{collectionId}/schema";
    checkPathParameter(
        extensionRegistry, api.getData(), definitionPath, "collectionId", collectionId);

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();

    @SuppressWarnings("unchecked")
    List<Profile> requestedProfiles =
        (List<Profile>)
            Objects.requireNonNullElse(
                queryParameterSet.getTypedValues().get(QueryParameterProfileSchema.PROFILE),
                List.of());

    SchemaConfiguration schemaConfiguration =
        api.getData()
            .getExtension(SchemaConfiguration.class, collectionId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Schema configuration not found for collection: " + collectionId));

    List<Profile> defaultProfilesSchema =
        extensionRegistry.getExtensionsForType(Profile.class).stream()
            .filter(
                profile ->
                    schemaConfiguration.getDefaultProfiles().containsKey(profile.getProfileSet())
                        && profile
                            .getId()
                            .equals(
                                schemaConfiguration
                                    .getDefaultProfiles()
                                    .get(profile.getProfileSet())))
            .toList();

    QueryInputCollectionProperties queryInput =
        new ImmutableQueryInputCollectionProperties.Builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .profiles(requestedProfiles)
            .defaultProfilesResource(defaultProfilesSchema)
            .type(CollectionPropertiesType.RETURNABLES_AND_RECEIVABLES)
            .schemaCache(this.schemaCache)
            .build();

    return queryHandler.handle(Query.COLLECTION_PROPERTIES, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(valueStore);
  }
}
