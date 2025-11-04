/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.additional.metadata.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.additional.metadata.domain.AdditionalMetadataConfiguration;
import de.ii.ogcapi.common.domain.ImmutableLandingPage;
import de.ii.ogcapi.common.domain.LandingPageExtension;
import de.ii.ogcapi.common.domain.OgcApiExtent;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class MetadataOnLandingPage implements LandingPageExtension {

  @Inject
  MetadataOnLandingPage() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return AdditionalMetadataConfiguration.class;
  }

  @Override
  public ImmutableLandingPage.Builder process(
      ImmutableLandingPage.Builder landingPageBuilder,
      OgcApi api,
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {

    OgcApiDataV2 apiData = api.getData();
    if (isEnabledForApi(apiData)) {
      apiData
          .getMetadata()
          .flatMap(ApiMetadata::getLicense)
          .ifPresent(spdx -> landingPageBuilder.putExtensions("license", spdx));

      apiData
          .getMetadata()
          .flatMap(ApiMetadata::getAttribution)
          .ifPresent(attribution -> landingPageBuilder.putExtensions("attribution", attribution));

      apiData
          .getExternalDocs()
          .ifPresent(
              externalDocumentation ->
                  landingPageBuilder.putExtensions("externalDocs", externalDocumentation));

      OgcApiExtent.of(api.getSpatialExtent(), api.getTemporalExtent())
          .ifPresent(extent -> landingPageBuilder.putExtensions("extent", extent));
    }

    return landingPageBuilder;
  }
}
