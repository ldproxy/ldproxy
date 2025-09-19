/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain;

import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import de.ii.xtraplatform.tiles.domain.TileGenerationContext;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TileFromFeatureQuery {

  FeatureQuery getQuery(
      Tile tile,
      List<OgcApiQueryParameter> allowedParameters,
      Map<String, String> queryParameters,
      TilesConfiguration tilesConfiguration,
      URICustomizer uriCustomizer);

  class MultiLayerTileContent {
    public byte[] byteArray;
    public boolean isComplete;
  }

  double getMaxAllowableOffset(Tile tile);

  default Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      TileGenerationContext transformationContext) {
    return Optional.empty();
  }

  default Optional<PropertyTransformations> getPropertyTransformations(
      FeatureTypeConfigurationOgcApi collectionData,
      Class<? extends ExtensionConfiguration> clazz) {

    Optional<PropertyTransformations> coreTransformations =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .map(
                featuresCoreConfiguration -> ((PropertyTransformations) featuresCoreConfiguration));

    Optional<PropertyTransformations> formatTransformations =
        collectionData
            .getExtension(clazz)
            .filter(
                buildingBlockConfiguration ->
                    buildingBlockConfiguration instanceof PropertyTransformations)
            .map(
                buildingBlockConfiguration ->
                    ((PropertyTransformations) buildingBlockConfiguration));

    return formatTransformations
        .map(ft -> coreTransformations.map(ft::mergeInto).orElse(ft))
        .or(() -> coreTransformations);
  }

  default Optional<PropertyTransformations> getPropertyTransformations(
      FeatureTypeConfigurationOgcApi collectionData,
      Map<String, String> substitutions,
      Class<? extends ExtensionConfiguration> clazz) {
    return getPropertyTransformations(collectionData, clazz)
        .map(propertyTransformations -> propertyTransformations.withSubstitutions(substitutions));
  }
}
