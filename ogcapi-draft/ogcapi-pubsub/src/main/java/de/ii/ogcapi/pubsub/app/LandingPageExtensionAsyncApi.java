/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.pubsub.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.ImmutableLandingPage.Builder;
import de.ii.ogcapi.common.domain.LandingPageExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.pubsub.domain.AsyncApiDefinitionFormatExtension;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class LandingPageExtensionAsyncApi implements LandingPageExtension {

  private final I18n i18n;
  private final ExtensionRegistry extensionRegistry;

  @Inject
  public LandingPageExtensionAsyncApi(ExtensionRegistry extensionRegistry, I18n i18n) {
    this.extensionRegistry = extensionRegistry;
    this.i18n = i18n;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return PubSubConfiguration.class;
  }

  @Override
  public Builder process(
      Builder landingPageBuilder,
      OgcApi api,
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {

    if (!isEnabledForApi(api.getData())) {
      return landingPageBuilder;
    }

    extensionRegistry.getExtensionsForType(AsyncApiDefinitionFormatExtension.class).stream()
        .filter(f -> f.isEnabledForApi(api.getData()))
        .filter(f -> f.getRel().isPresent())
        .sorted(Comparator.comparing(f -> f.getMediaType().parameter()))
        .forEach(
            f ->
                landingPageBuilder.addLinks(
                    new ImmutableLink.Builder()
                        .href(
                            uriCustomizer
                                .copy()
                                .ensureLastPathSegment("asyncapi")
                                .setParameter("f", f.getMediaType().parameter())
                                .toString())
                        .rel(f.getRel().get())
                        .type(f.getMediaType().type().toString())
                        .title(
                            i18n.get(
                                f.getRel().get().equals("service-desc")
                                    ? "serviceDescLinkPubSub"
                                    : "serviceDocLinkPubSub",
                                language))
                        .build()));

    return landingPageBuilder;
  }
}
