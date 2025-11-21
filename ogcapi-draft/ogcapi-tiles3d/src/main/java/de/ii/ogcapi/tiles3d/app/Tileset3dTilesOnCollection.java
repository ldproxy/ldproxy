/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.common.domain.OgcApiExtent;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** add a link to the 3D Tiles tileset to the collection */
@Singleton
@AutoBind
public class Tileset3dTilesOnCollection implements CollectionExtension {

  private final Tile3dProviders tile3dProviders;
  private final I18n i18n;

  @Inject
  public Tileset3dTilesOnCollection(Tile3dProviders tile3dProviders, I18n i18n) {
    this.tile3dProviders = tile3dProviders;
    this.i18n = i18n;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public Builder process(
      Builder collection,
      FeatureTypeConfigurationOgcApi featureTypeConfiguration,
      OgcApi api,
      URICustomizer uriCustomizer,
      boolean isNested,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    if (isExtensionEnabled(featureTypeConfiguration, getBuildingBlockConfigurationType())) {
      URICustomizer uriCustomizerTileset = uriCustomizer.copy().ensureNoTrailingSlash();
      if (isNested) {
        uriCustomizerTileset =
            uriCustomizerTileset
                .ensureLastPathSegments(featureTypeConfiguration.getId(), "3dtiles")
                .removeParameters("f");
      } else {
        uriCustomizerTileset = uriCustomizerTileset.ensureLastPathSegment("3dtiles");
      }
      if (!isExtensionEnabled(featureTypeConfiguration, FeaturesCoreConfiguration.class)) {
        if (isNested) {
          collection
              .title(featureTypeConfiguration.getLabel() + " (3D Tiles)")
              .addLinks(
                  new ImmutableLink.Builder()
                      .href(
                          uriCustomizer
                              .copy()
                              .ensureNoTrailingSlash()
                              .clearParameters()
                              .ensureLastPathSegments(
                                  "collections", featureTypeConfiguration.getId())
                              .toString())
                      .rel("self")
                      .title(
                          i18n.get("selfLinkCollection", language)
                              .replace("{{collection}}", featureTypeConfiguration.getLabel()))
                      .build());
        } else {
          collection.title(featureTypeConfiguration.getLabel());
        }
        collection
            .description(featureTypeConfiguration.getDescription())
            .itemType(FeaturesCoreConfiguration.ItemType.unknown.toString())
            .dataRel("http://www.opengis.net/def/rel/ogc/0.0/tileset-3dtiles");

        tile3dProviders
            .getTileset3dMetadata(api.getData(), featureTypeConfiguration)
            .ifPresent(
                tileset3d -> {
                  collection.extent(
                      OgcApiExtent.of(
                          tileset3d.getRoot().getBoundingVolume().toBoundingBox(),
                          Optional.empty()));
                });
      }

      collection.addAllLinks(
          ImmutableList.<Link>builder()
              .add(
                  new ImmutableLink.Builder()
                      .href(uriCustomizerTileset.toString())
                      // TODO rel is still unclear
                      .rel("http://www.opengis.net/def/rel/ogc/0.0/tileset-3dtiles")
                      .type("text/html")
                      .title(i18n.get("3dtilesLink", language))
                      .build())
              .add(
                  new ImmutableLink.Builder()
                      .href(uriCustomizerTileset.copy().setParameter("f", "json").toString())
                      // TODO rel is still unclear
                      .rel("http://www.opengis.net/def/rel/ogc/0.0/tileset-3dtiles")
                      // TODO see https://github.com/opengeospatial/ogcapi-3d-geovolumes/issues/13
                      .type("application/json")
                      // .type("application/json+3dtiles")
                      .title(i18n.get("3dtilesLink", language))
                      .build())
              .build());

      collection.putExtensions("collectionType", "3d-container");
      // TODO see https://github.com/opengeospatial/ogcapi-3d-geovolumes/issues/12
      collection.putExtensions("children", ImmutableList.of());
      // TODO see https://github.com/opengeospatial/ogcapi-3d-geovolumes/issues/12
      collection.putExtensions(
          "content",
          ImmutableList.of(
              new ImmutableLink.Builder()
                  .href(uriCustomizerTileset.copy().setParameter("f", "json").toString())
                  // TODO rel is still unclear
                  .rel("http://www.opengis.net/def/rel/ogc/0.0/tileset-3dtiles")
                  // TODO see https://github.com/opengeospatial/ogcapi-3d-geovolumes/issues/13
                  // .type("application/json+3dtiles")
                  .type("application/json")
                  .title(
                      i18n.get("3dtilesLink", language)
                          .replace("{{collection}}", featureTypeConfiguration.getLabel()))
                  .build()));
    }

    return collection;
  }
}
