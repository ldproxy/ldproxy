/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.ImmutableLandingPage.Builder;
import de.ii.ogcapi.common.domain.LandingPageExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// ToDo Language
@Singleton
@AutoBind
public class ProcessesOnLandingPage implements LandingPageExtension {

  private final I18n i18n;
  private final ExtensionRegistry extensionRegistry;

  @Inject
  public ProcessesOnLandingPage(ExtensionRegistry extensionRegistry, I18n i18n) {
    this.extensionRegistry = extensionRegistry;
    this.i18n = i18n;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  public Builder process(
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

    landingPageBuilder.addLinks(
        new ImmutableLink.Builder()
            .href(uriCustomizer.copy().ensureLastPathSegment("processes").toString())
            .rel("https://www.opengis.net/def/rel/ogc/1.0/processes")
            .title(i18n.get("processListLink", language))
            .build());

    return landingPageBuilder;
  }
}
