/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.EndpointFeaturesDefinition;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeature.Builder;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.ETag.Type;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaBase.Scope;
import io.dropwizard.auth.Auth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @title Feature
 * @path collections/{collectionId}/items/{featureId}
 * @langEn The response is a document representing the feature with the requested feature
 *     identifier.
 * @langDe Die Antwort ist ein Dokument, welches das Feature mit dem angeforderten Identifikator
 *     repräsentiert.
 * @ref:formats {@link de.ii.ogcapi.features.core.domain.FeatureFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointFeature extends EndpointFeaturesDefinition
    implements PolicyAttributeFeatureGetter, ApiExtensionHealth {

  private final FeaturesQuery ogcApiFeaturesQuery;
  private final FeaturesCoreQueriesHandler queryHandler;

  @Inject
  public EndpointFeature(
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      FeaturesQuery ogcApiFeaturesQuery,
      FeaturesCoreQueriesHandler queryHandler) {
    super(extensionRegistry, providers);
    this.ogcApiFeaturesQuery = ogcApiFeaturesQuery;
    this.queryHandler = queryHandler;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    // EndpointFeatures validates the configuration
    return super.onStartup(api, apiValidation);
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_FEATURES);

    generateDefinition(
        apiData,
        definitionBuilder,
        "/items/{featureId}",
        "retrieve a feature in the feature collection '",
        "Fetch the feature with id `{featureId}`.",
        "FEATURE");

    return definitionBuilder.build();
  }

  @GET
  @Path("/{collectionId}/items/{featureId}")
  public Response getItem(
      @Auth Optional<User> optionalUser,
      @Context ApiRequestContext requestContext,
      @PathParam("collectionId") String collectionId,
      @PathParam("featureId") String featureId) {
    return getItem(requestContext, collectionId, featureId);
  }

  @Override
  public Response getItem(ApiRequestContext requestContext, String collectionId, String featureId) {
    OgcApi api = requestContext.getApi();

    checkCollectionExists(api.getData(), collectionId);

    FeatureTypeConfigurationOgcApi collectionData =
        api.getData().getCollections().get(collectionId);

    FeaturesCoreConfiguration coreConfiguration =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .filter(
                cfg ->
                    cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                        != FeaturesCoreConfiguration.ItemType.unknown)
            .orElseThrow(() -> new NotFoundException("Features are not supported for this API."));

    providers
        .getFeatureSchema(api.getData(), collectionData)
        .flatMap(SchemaBase::getIdProperty)
        .filter(idProperty -> SchemaBase.Type.INTEGER.equals(idProperty.getType()))
        .ifPresent(ignore -> checkFeatureIdIsInteger(featureId));

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();

    List<Profile> requestedProfiles =
        (List<Profile>)
            Objects.requireNonNullElse(
                queryParameterSet.getTypedValues().get(QueryParameterProfileFeatures.PROFILE),
                List.of());

    FeatureQuery query =
        ogcApiFeaturesQuery.requestToFeatureQuery(
            api.getData(),
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            queryParameterSet,
            featureId,
            Optional.of(Type.STRONG),
            Scope.RETURNABLE);

    Builder queryInputBuilder =
        new Builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .featureId(featureId)
            .query(query)
            .profiles(requestedProfiles)
            .featureProvider(providers.getFeatureProviderOrThrow(api.getData(), collectionData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs());

    if (Objects.nonNull(coreConfiguration.getCaching())
        && Objects.nonNull(coreConfiguration.getCaching().getCacheControlItems()))
      queryInputBuilder.cacheControl(coreConfiguration.getCaching().getCacheControlItems());

    return queryHandler.handle(
        FeaturesCoreQueriesHandler.Query.FEATURE, queryInputBuilder.build(), requestContext);
  }

  private void checkFeatureIdIsInteger(String featureId) {
    try {
      Integer.parseInt(featureId);
    } catch (NumberFormatException e) {
      throw new NotFoundException("The requested feature does not exist.");
    }
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(ogcApiFeaturesQuery, queryHandler, providers.getFeatureProviderOrThrow(apiData));
  }
}
