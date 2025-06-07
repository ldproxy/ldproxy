/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.EndpointFeaturesDefinition;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesCoreValidation;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures.Builder;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import io.dropwizard.auth.Auth;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
 * @title Features
 * @path collections/{collectionId}/items
 * @langEn The response is a document consisting of features in the collection. The features
 *     included in the response are determined by the server based on the query parameters of the
 *     request.
 * @langDe Die Antwort ist ein Dokument, das aus Features der Collection besteht. Die in der Antwort
 *     enthaltenen Features werden vom Server auf der Grundlage der Abfrageparameter der Anfrage
 *     bestimmt.
 * @ref:formats {@link de.ii.ogcapi.features.core.domain.FeatureFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointFeatures extends EndpointFeaturesDefinition
    implements PolicyAttributeFeaturesGetter, ApiExtensionHealth {

  private final Values<Codelist> codelistStore;
  private final FeaturesQuery ogcApiFeaturesQuery;
  private final FeaturesCoreQueriesHandler queryHandler;
  private final FeaturesCoreValidation featuresCoreValidator;

  @Inject
  public EndpointFeatures(
      ExtensionRegistry extensionRegistry,
      ValueStore valueStore,
      FeaturesCoreProviders providers,
      FeaturesQuery ogcApiFeaturesQuery,
      FeaturesCoreQueriesHandler queryHandler,
      FeaturesCoreValidation featuresCoreValidator) {
    super(extensionRegistry, providers);
    this.codelistStore = valueStore.forType(Codelist.class);
    this.ogcApiFeaturesQuery = ogcApiFeaturesQuery;
    this.queryHandler = queryHandler;
    this.featuresCoreValidator = featuresCoreValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(FeatureFormatExtension.class);
    return formats;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ValidationResult result = super.onStartup(api, apiValidation);

    // no additional operational checks for now, only validation; we can stop, if no validation is
    // requested
    if (apiValidation == MODE.NONE) return result;

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().from(result).mode(apiValidation);

    Map<String, FeatureSchema> featureSchemas = providers.getFeatureSchemas(api.getData());

    List<String> invalidCollections =
        featuresCoreValidator.getCollectionsWithoutType(api.getData(), featureSchemas);
    for (String invalidCollection : invalidCollections) {
      builder.addStrictErrors(
          MessageFormat.format(
              "The Collection ''{0}'' is invalid, because its feature type was not found in the provider schema.",
              invalidCollection));
    }

    // get Features Core configurations to process
    Map<String, FeaturesCoreConfiguration> coreConfigs =
        api.getData().getCollections().entrySet().stream()
            .map(
                entry -> {
                  final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
                  final FeaturesCoreConfiguration config =
                      collectionData.getExtension(FeaturesCoreConfiguration.class).orElse(null);
                  if (Objects.isNull(config)) return null;
                  return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), config);
                })
            .filter(Objects::nonNull)
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    Map<String, Collection<String>> transformationKeys =
        coreConfigs.entrySet().stream()
            .map(
                entry ->
                    new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(), entry.getValue().getTransformations().keySet()))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    for (Map.Entry<String, Collection<String>> stringCollectionEntry :
        featuresCoreValidator
            .getInvalidPropertyKeys(transformationKeys, featureSchemas)
            .entrySet()) {
      for (String property : stringCollectionEntry.getValue()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "A transformation for property ''{0}'' in collection ''{1}'' is invalid, because the property was not found in the provider schema.",
                property, stringCollectionEntry.getKey()));
      }
    }

    for (Map.Entry<String, FeaturesCoreConfiguration> entry : coreConfigs.entrySet()) {
      String collectionId = entry.getKey();
      FeaturesCoreConfiguration config = entry.getValue();
      if (config.getMinimumPageSize() < 1) {
        builder.addStrictErrors(
            MessageFormat.format(
                "The minimum page size ''{0}'' in collection ''{1}'' is invalid, it must be a positive integer.",
                config.getMinimumPageSize(), collectionId));
      }
      if (config.getMinimumPageSize() > config.getMaximumPageSize()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "The minimum page size ''{0}'' in collection ''{1}'' is invalid, it cannot be greater than the maximum page size ''{2}''.",
                config.getMinimumPageSize(), collectionId, config.getMaximumPageSize()));
      }
      if (config.getMinimumPageSize() > config.getDefaultPageSize()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "The minimum page size ''{0}'' in collection ''{1}'' is invalid, it cannot be greater than the default page size ''{2}''.",
                config.getMinimumPageSize(), collectionId, config.getDefaultPageSize()));
      }
      if (config.getMaximumPageSize() < config.getDefaultPageSize()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "The maxmimum page size ''{0}'' in collection ''{1}'' is invalid, it must be at least the default page size ''{2}''.",
                config.getMaximumPageSize(), collectionId, config.getDefaultPageSize()));
      }
    }

    for (Map.Entry<String, FeaturesCoreConfiguration> entry : coreConfigs.entrySet()) {
      String collectionId = entry.getKey();
      for (Map.Entry<String, List<PropertyTransformation>> entry2 :
          entry.getValue().getTransformations().entrySet()) {
        String property = entry2.getKey();
        for (PropertyTransformation transformation : entry2.getValue()) {
          builder = transformation.validate(builder, collectionId, property, codelistStore.ids());
        }
      }
    }

    return builder.build();
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
        "/items",
        "retrieve features in the feature collection '",
        String.format(
            "The response is a document consisting of features in the collection. "
                + "The features included in the response are determined by the server based on the query parameters of the request.\n\n"
                + "To support access to larger collections without overloading the client, the API supports paged access with links "
                + "to the next page, if more features are selected that the page size (maximum page size is %d).\n\nThe `bbox` and `datetime` parameter can be "
                + "used to select only a subset of the features in the collection (the features that are in the bounding box or time interval). "
                + "The `bbox` parameter matches all features in the collection that are not associated with a location, too. "
                + "The `datetime` parameter matches all features in the collection that are not associated with a time stamp or interval, too. "
                + "The `limit` parameter may be used to control the subset of the selected features that should be returned in the response, "
                + "the page size. Each page may include information about the number of selected and returned features (`numberMatched` "
                + "and `numberReturned`) as well as links to support paging (link relation `next`).\n\nSee the details of this operation for "
                + "a description of additional query parameters supported by this resource.",
            apiData
                .getExtension(FeaturesCoreConfiguration.class)
                .map(FeaturesCoreConfiguration::getMaximumPageSize)
                .get()),
        "FEATURES");

    return definitionBuilder.build();
  }

  @GET
  @Path("/{collectionId}/items")
  public Response getItems(
      @Auth Optional<User> optionalUser,
      @Context ApiRequestContext requestContext,
      @PathParam("collectionId") String collectionId) {
    return getItems(requestContext, collectionId);
  }

  @Override
  public Response getItems(ApiRequestContext requestContext, String collectionId) {
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
            .orElseThrow(
                () ->
                    new NotFoundException(
                        MessageFormat.format(
                            "Features are not supported in API ''{0}'', collection ''{1}''.",
                            api.getId(), collectionId)));

    int defaultPageSize = coreConfiguration.getDefaultPageSize();

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
            defaultPageSize,
            queryParameterSet);
    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        new Builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .query(query)
            .profiles(requestedProfiles)
            .featureProvider(providers.getFeatureProviderOrThrow(api.getData(), collectionData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .defaultPageSize(Optional.of(defaultPageSize))
            .build();

    return queryHandler.handle(
        FeaturesCoreQueriesHandlerImpl.Query.FEATURES, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(ogcApiFeaturesQuery, queryHandler, providers.getFeatureProviderOrThrow(apiData));
  }
}
