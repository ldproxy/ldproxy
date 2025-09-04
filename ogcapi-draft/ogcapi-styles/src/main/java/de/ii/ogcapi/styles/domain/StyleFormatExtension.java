/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.values.domain.Values;
import java.util.List;
import java.util.Optional;

/** ApiExtension for a style encoding at /{serviceId}/styles/{styleId} */
@AutoMultiBind
public interface StyleFormatExtension extends FormatExtension {

  @Override
  default boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(StylesConfiguration.class)
        .filter(StylesConfiguration::isEnabled)
        .filter(config -> config.getStyleEncodings().contains(this.getMediaType().label()))
        .isPresent();
  }

  @Override
  default boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getCollections()
        .get(collectionId)
        .getExtension(StylesConfiguration.class)
        .filter(StylesConfiguration::isEnabled)
        .filter(config -> config.getStyleEncodings().contains(this.getMediaType().label()))
        .isPresent();
  }

  @Override
  default Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  /**
   * @return the file extension used for the stylesheets in the style store
   */
  String getFileExtension();

  /**
   * @return the specification URL for the style metadata
   */
  String getSpecification();

  /**
   * @return the version for the style metadata
   */
  String getVersion();

  /**
   * returns the title of a style
   *
   * @param styleId the id of the style
   * @param stylesheetContent the stylesheetContent content
   * @return the title of the style, if applicable, or the style identifier
   */
  default String getTitle(String styleId, StylesheetContent stylesheetContent) {
    return styleId;
  }

  /**
   * @return {@code true}, if the style cannot be stored in the style encoding, but only be derived
   *     from an existing style encoding
   */
  default boolean getDerived() {
    return false;
  }

  /**
   * @return {@code true}, if it is a style encoding that can be used for a default style
   */
  default boolean getAsDefault() {
    return false;
  }

  /**
   * @return
   */
  default boolean canDeriveCollectionStyle() {
    return false;
  }

  /**
   * @param stylesheetContent
   * @param apiData - the api, source must use the API Id
   * @param collectionId - the collection, all layers of other collections will be removed,
   *     source-layer must use the collectionId
   * @return
   */
  default Optional<StylesheetContent> deriveCollectionStyle(
      StylesheetContent stylesheetContent,
      OgcApiDataV2 apiData,
      String collectionId,
      String styleId) {
    return Optional.empty();
  }

  /**
   * @return
   */
  default boolean canDeriveMetadata() {
    return false;
  }

  /**
   * @return
   */
  default List<StyleLayer> deriveLayerMetadata(
      StylesheetContent stylesheetContent,
      OgcApiDataV2 apiData,
      FeaturesCoreProviders providers,
      Values<Codelist> entityRegistry) {
    return ImmutableList.of();
  }

  /**
   * @param stylesheetContent the stylesheet content
   * @param api
   * @param tileMatrixSet
   * @param requestContext
   * @return the response
   */
  default Object getStyleEntity(
      StylesheetContent stylesheetContent,
      OgcApi api,
      Optional<String> collectionId,
      String styleId,
      Optional<TileMatrixSet> tileMatrixSet,
      ApiRequestContext requestContext) {
    return stylesheetContent.getContent();
  }

  /**
   * validate the content of the stylesheet
   *
   * @param stylesheetContent the stylesheet content
   * @param strict strict or lenient validation
   * @return the derived id
   */
  default Optional<String> analyze(StylesheetContent stylesheetContent, boolean strict) {
    return Optional.empty();
  }
}
