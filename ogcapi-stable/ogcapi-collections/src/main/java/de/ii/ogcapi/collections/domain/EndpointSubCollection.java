/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.ParameterExtension;
import de.ii.ogcapi.foundation.domain.RuntimeQueryParametersExtension;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;

public abstract class EndpointSubCollection extends Endpoint {

  public EndpointSubCollection(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData.getCollections().values().stream()
        .filter(FeatureTypeConfigurationOgcApi::getEnabled)
        .anyMatch(featureType -> isEnabledForApi(apiData, featureType.getId()));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    FeatureTypeConfigurationOgcApi configuration = apiData.getCollections().get(collectionId);
    return super.isEnabledForApi(apiData, collectionId)
        && Objects.nonNull(configuration)
        && configuration.getEnabled();
  }

  protected void checkCollectionExists(
      @Context OgcApiDataV2 apiData, @PathParam("collectionId") String collectionId) {
    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }
  }

  public List<OgcApiQueryParameter> getQueryParameters(
      ExtensionRegistry extensionRegistry,
      OgcApiDataV2 apiData,
      String definitionPath,
      String collectionId) {
    return getQueryParameters(
        extensionRegistry, apiData, definitionPath, collectionId, HttpMethods.GET);
  }

  public List<OgcApiQueryParameter> getQueryParameters(
      ExtensionRegistry extensionRegistry,
      OgcApiDataV2 apiData,
      String definitionPath,
      String collectionId,
      HttpMethods method) {
    if (collectionId.equals("{collectionId}")) {
      Optional<String> representativeCollectionId = getRepresentativeCollectionId(apiData);
      if (representativeCollectionId.isEmpty())
        return getQueryParameters(extensionRegistry, apiData, definitionPath, method);

      collectionId = representativeCollectionId.get();
    }
    String finalCollectionId = collectionId;
    return Stream.concat(
            extensionRegistry.getExtensionsForType(OgcApiQueryParameter.class).stream()
                .filter(
                    param -> param.isApplicable(apiData, definitionPath, finalCollectionId, method))
                .sorted(Comparator.comparing(ParameterExtension::getName)),
            extensionRegistry.getExtensionsForType(RuntimeQueryParametersExtension.class).stream()
                .map(
                    extension ->
                        extension.getRuntimeParameters(
                            apiData, Optional.of(finalCollectionId), definitionPath, method))
                .flatMap(Collection::stream))
        .collect(ImmutableList.toImmutableList());
  }

  public ImmutableList<ApiHeader> getHeaders(
      ExtensionRegistry extensionRegistry,
      OgcApiDataV2 apiData,
      String definitionPath,
      String collectionId,
      HttpMethods method) {
    // TODO or do we need collection-specific headers?
    return getHeaders(extensionRegistry, apiData, definitionPath, method);
  }

  protected Optional<String> getRepresentativeCollectionId(OgcApiDataV2 apiData) {
    if (apiData
        .getExtension(CollectionsConfiguration.class)
        .filter(config -> config.getCollectionDefinitionsAreIdentical().orElse(false))
        .isPresent())
      return Optional.ofNullable(apiData.getCollections().keySet().iterator().next());

    return Optional.empty();
  }

  @Override
  protected String getOperationId(String name, String... prefixes) {
    // prefixes is never empty and the first prefix is the collectionId or the collectionId template
    if ("{collectionId}".equals(prefixes[0])) {
      prefixes[0] = "collection";
    }
    return super.getOperationId(name, prefixes);
  }

  /* TODO do we need collection-specific path parameters? The API definitions would need to be adapted for this, too
  ImmutableList<OgcApiPathParameter> getPathParameters(ExtensionRegistry extensionRegistry, OgcApiDataV2 apiData, String definitionPath, String collectionId) {
      return extensionRegistry.getExtensionsForType(OgcApiPathParameter.class)
              .stream()
              .filter(param -> param.isApplicable(apiData, definitionPath, collectionId))
              .collect(ImmutableSet.toImmutableSet());
  }
  */
}
