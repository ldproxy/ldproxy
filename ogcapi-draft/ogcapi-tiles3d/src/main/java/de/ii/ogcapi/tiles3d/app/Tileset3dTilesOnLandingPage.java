/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.ImmutableLandingPage;
import de.ii.ogcapi.common.domain.LandingPageExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** add a link to the 3D Tiles tileset to the landing page */
@Singleton
@AutoBind
public class Tileset3dTilesOnLandingPage implements LandingPageExtension {

  private final I18n i18n;

  @Inject
  public Tileset3dTilesOnLandingPage(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public ImmutableLandingPage.Builder process(
      ImmutableLandingPage.Builder landingPageBuilder,
      OgcApi api,
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    /*api.getData().getCollections().entrySet().stream()
    .filter(entry -> isExtensionEnabled(entry.getValue(), getBuildingBlockConfigurationType()))
    .forEach(
        entry -> {
          URICustomizer uriCustomizerTileset =
              uriCustomizer
                  .copy()
                  .ensureNoTrailingSlash()
                  .removeParameters("f")
                  .ensureLastPathSegments("collections", entry.getKey(), "3dtiles");
          landingPageBuilder.addAllLinks(
              ImmutableList.<Link>builder()
                  .add(
                      new ImmutableLink.Builder()
                          .href(uriCustomizerTileset.toString())
                          // TODO rel is still unclear
                          .rel("http://www.opengis.net/def/rel/ogc/0.0/tileset-3dtiles")
                          // TODO see
                          // https://github.com/opengeospatial/ogcapi-3d-geovolumes/issues/13
                          .type("application/json")
                          // .type("application/json+3dtiles")
                          .title(
                              i18n.get("3dtilesLink", language)
                                  .replace("{{collection}}", entry.getValue().getLabel()))
                          .build())
                  .build());
        });*/

    return landingPageBuilder;
  }
}
