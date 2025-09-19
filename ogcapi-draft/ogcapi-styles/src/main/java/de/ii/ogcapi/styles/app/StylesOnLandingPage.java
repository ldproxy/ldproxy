/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.ImmutableLandingPage;
import de.ii.ogcapi.common.domain.ImmutableLandingPage.Builder;
import de.ii.ogcapi.common.domain.LandingPageExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
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

/** add styles information to the landing page */
@Singleton
@AutoBind
public class StylesOnLandingPage implements LandingPageExtension {

  private final I18n i18n;
  private final StyleRepository styleRepo;

  @Inject
  public StylesOnLandingPage(I18n i18n, StyleRepository styleRepo) {
    this.i18n = i18n;
    this.styleRepo = styleRepo;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public ImmutableLandingPage.Builder process(
      Builder landingPageBuilder,
      OgcApi api,
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    OgcApiDataV2 apiData = api.getData();
    if (!isEnabledForApi(apiData)) {
      return landingPageBuilder;
    }

    final StylesLinkGenerator stylesLinkGenerator = new StylesLinkGenerator();

    Optional<String> defaultStyle =
        apiData
            .getExtension(HtmlConfiguration.class)
            .map(HtmlConfiguration::getDefaultStyle)
            .filter(s -> !s.equals("NONE"));
    if (defaultStyle.isPresent()) {
      Optional<StyleFormatExtension> htmlStyleFormat =
          styleRepo
              .getStyleFormatStream(apiData, Optional.empty())
              .filter(f -> f.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE))
              .findAny();
      if (htmlStyleFormat.isPresent()
          && !styleRepo.stylesheetExists(
              apiData, Optional.empty(), defaultStyle.get(), htmlStyleFormat.get(), true))
        defaultStyle = Optional.empty();
    }
    List<Link> links =
        stylesLinkGenerator.generateLandingPageLinks(uriCustomizer, defaultStyle, i18n, language);
    landingPageBuilder.addAllLinks(links);

    return landingPageBuilder;
  }
}
