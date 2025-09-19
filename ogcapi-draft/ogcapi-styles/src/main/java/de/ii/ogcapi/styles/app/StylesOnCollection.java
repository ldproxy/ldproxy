/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StyleRepository;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.ogcapi.styles.domain.StylesLinkGenerator;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

/** add styles information to the collection */
@Singleton
@AutoBind
public class StylesOnCollection implements CollectionExtension {

  private final I18n i18n;
  private final StyleRepository styleRepo;

  @Inject
  public StylesOnCollection(I18n i18n, StyleRepository styleRepo) {
    this.styleRepo = styleRepo;
    this.i18n = i18n;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public ImmutableOgcApiCollection.Builder process(
      Builder collection,
      FeatureTypeConfigurationOgcApi featureTypeConfiguration,
      OgcApi api,
      URICustomizer uriCustomizer,
      boolean isNested,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    // skip link on /collections
    if (isNested) return collection;

    OgcApiDataV2 apiData = api.getData();
    // nothing to add if disabled
    String collectionId = featureTypeConfiguration.getId();
    if (!isEnabledForApi(apiData, collectionId)) {
      return collection;
    }

    Optional<String> defaultStyle =
        apiData
            .getCollections()
            .get(collectionId)
            .getExtension(HtmlConfiguration.class)
            .map(HtmlConfiguration::getDefaultStyle)
            .filter(s -> !s.equals("NONE"));
    if (defaultStyle.isPresent()) {
      Optional<StyleFormatExtension> htmlStyleFormat =
          styleRepo
              .getStyleFormatStream(apiData, Optional.of(collectionId))
              .filter(f -> f.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE))
              .findAny();
      if (htmlStyleFormat.isPresent()
          && !styleRepo.stylesheetExists(
              apiData, Optional.of(collectionId), defaultStyle.get(), htmlStyleFormat.get(), true))
        defaultStyle = Optional.empty();
    }
    return collection.addAllLinks(
        new StylesLinkGenerator()
            .generateCollectionLinks(uriCustomizer, defaultStyle, i18n, language));
  }
}
