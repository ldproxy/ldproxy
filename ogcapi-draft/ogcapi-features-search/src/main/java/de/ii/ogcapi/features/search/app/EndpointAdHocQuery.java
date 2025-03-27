/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.GROUP_DATA_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.features.core.domain.EndpointRequiresFeatures;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputQuery;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.Query;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.QueryInputQuery;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @title Ad-hoc Query
 * @path search
 * @langEn Execute an ad-hoc query
 * @langDe Eine Ad-hoc-Query ausführen
 * @ref:formats {@link de.ii.ogcapi.features.core.domain.FeatureFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointAdHocQuery extends EndpointRequiresFeatures
    implements ConformanceClass, ApiExtensionHealth {

  private static final List<String> TAGS = ImmutableList.of("Discover and execute queries");
  private static final ApiMediaType REQUEST_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.APPLICATION_JSON_TYPE)
          .label("JSON")
          .parameter("json")
          .build();

  private final SearchQueriesHandler queryHandler;
  private final FeaturesCoreProviders providers;
  private final Schema<?> schema;
  private final Map<String, Schema<?>> referencedSchemas;

  @Inject
  public EndpointAdHocQuery(
      ExtensionRegistry extensionRegistry,
      SearchQueriesHandler queryHandler,
      FeaturesCoreProviders providers,
      ClassSchemaCache classSchemaCache) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.providers = providers;
    this.schema = classSchemaCache.getSchema(QueryExpression.class);
    this.referencedSchemas = classSchemaCache.getReferencedSchemas(QueryExpression.class);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ValidationResult result = super.onStartup(api, apiValidation);

    if (apiValidation == MODE.NONE) {
      return result;
    }

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().from(result).mode(apiValidation);

    return builder.build();
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(FeatureFormatExtension.class);
    }
    return formats;
  }

  @Override
  public Map<MediaType, ApiMediaTypeContent> getRequestContent(OgcApiDataV2 apiData) {
    return ImmutableMap.of(
        REQUEST_MEDIA_TYPE.type(),
        new ImmutableApiMediaTypeContent.Builder()
            .ogcApiMediaType(REQUEST_MEDIA_TYPE)
            .schema(schema)
            .schemaRef(QueryExpression.SCHEMA_REF)
            .referencedSchemas(referencedSchemas)
            .build());
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

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("search")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_SEARCH_AD_HOC);
    String path = "/search";
    HttpMethods method = HttpMethods.POST;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path, method);
    List<ApiHeader> headers = getHeaders(extensionRegistry, apiData, path, method);
    String operationSummary = "execute an ad-hoc query";
    String description =
        "An ad-hoc query expression is expressed as a JSON object. The query expression object "
            + "can describe a single query (the properties \"collections\", \"filter\", "
            + "\"properties\" and \"sortby\" are members of the query expression object) or "
            + "multiple queries (a \"queries\" member with an array of query objects is present) "
            + "in a single request.\n"
            + "<p>For each query:</p>"
            + "<ul><li>The value of \"collection\" is an array with one item, the identifier of the "
            + "collection to query.\n"
            + "<li>The value of \"filter\" is a CQL2 JSON filter expression.\n"
            + "<li>The value of \"properties\" is an array with the names of properties to include "
            + "in the response.\n"
            + "<li>The value of \"sortby\" is used to sort the features in the response.\n</ul>"
            + "<p>For multiple queries:</p>"
            + "<ul><li>If multiple queries are specified, the results are concatenated. The response "
            + "is a single feature collection. The feature ids in the response to a "
            + "multi-collection query must be unique. Since the identifier of a feature only has to "
            + "be unique per collection, they need to be combined with the collection identifier. "
            + "A concatenation with \".\" as the joining character is used (e.g., "
            + "\"apronelement.123456\").\n"
            + "<li>The direct members \"filter\" and \"properties\" represent \"global\" "
            + "constraints that must be combined with the corresponding member in each query. The "
            + "global and local property selection list are concatenated and then the global and "
            + "local filters are combined using the logical operator specified by the "
            + "\"filterOperator\" member.\n"
            + "<li>The global member \"filter\" must only reference queryables that are common to "
            + "all collections being queried.\n"
            + "<li>The global member \"properties\" must only reference presentables that are "
            + "common to all collections being queried.\n</ul>"
            + "<p>General remarks:</p>"
            + "<ul><li>A \"title\" and \"description\" for the query expression can be added. "
            + "Providing both is strongly recommended to explain the query to users.\n"
            + "<li>The \"limit\" member applies to the entire result set.\n"
            + "<li>\"sortby\" will only apply per query. A global \"sortby\" would require that "
            + "the results of all queries are compiled first and then the combined result set is "
            + "sorted. This would not support \"streaming\" the response.\n"
            + "<li>In case of a parameterized stored query, the query expression may contain JSON "
            + "objects with a member \"$parameter\". The value of \"$parameter\" is an object with "
            + "a member where the key is the parameter name and the value is a JSON schema "
            + "describing the parameter. When executing the stored query, all objects with a "
            + "\"$parameter\" member are replaced with the value of the parameter for this query "
            + "execution. Comma-separated parameter values are converted to an array, if the "
            + "parameter is of type \"array\".\n"
            + "<li>Parameters may also be provided in a member \"parameters\" in the query "
            + "expression and referenced using \"$ref\".</ul>";
    Optional<String> operationDescription = Optional.of(description);
    ImmutableOgcApiResourceData.Builder resourceBuilder =
        new ImmutableOgcApiResourceData.Builder().path(path);
    Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(apiData);
    Map<MediaType, ApiMediaTypeContent> responseContent =
        getFeatureContent(getResourceFormats(), apiData, Optional.empty(), true);
    ApiOperation.of(
            requestContent,
            responseContent,
            queryParameters,
            headers,
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("getAdhocQuery"),
            GROUP_DATA_READ,
            TAGS,
            SearchBuildingBlock.MATURITY,
            SearchBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(method.toString(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  @POST
  @Path("/")
  public Response getAdhocQuery(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpServletRequest request,
      InputStream requestBody) {

    OgcApiDataV2 apiData = api.getData();
    ensureSupportForFeatures(apiData);

    QueryExpression query;
    try {
      query = QueryExpression.of(requestBody);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("The content of the query expression is invalid: %s", e.getMessage()), e);
    }

    FeaturesCoreConfiguration coreConfiguration =
        apiData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();

    QueryInputQuery queryInput =
        new ImmutableQueryInputQuery.Builder()
            .from(getGenericQueryInput(apiData))
            .query(query)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .minimumPageSize(Optional.ofNullable(coreConfiguration.getMinimumPageSize()))
            .defaultPageSize(Optional.ofNullable(coreConfiguration.getDefaultPageSize()))
            .maximumPageSize(Optional.ofNullable(coreConfiguration.getMaximumPageSize()))
            .allLinksAreLocal(
                api.getData()
                    .getExtension(SearchConfiguration.class)
                    .map(SearchConfiguration::getAllLinksAreLocal)
                    .orElse(false))
            .isStoredQuery(false)
            .build();

    return queryHandler.handle(Query.QUERY, queryInput, requestContext);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(
        "http://www.opengis.net/spec/ogcapi-features-n/0.0/conf/ad-hoc-queries");
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, providers.getFeatureProviderOrThrow(apiData));
  }
}
