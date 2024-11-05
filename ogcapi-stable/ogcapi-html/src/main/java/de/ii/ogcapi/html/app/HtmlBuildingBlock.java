/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.ImmutableHtmlConfiguration;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.AuthConfiguration.AuthProvider;
import de.ii.xtraplatform.base.domain.AuthConfiguration.IdentityProvider;
import de.ii.xtraplatform.base.domain.AuthConfiguration.LoginProvider;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 * @langEn HTML encoding for every supported resource.
 * @langDe HTML-Kodierung für alle unterstützten Ressourcen.
 * @conformanceEn *JSON* implements all requirements of conformance class *HTML* from [OGC API -
 *     Features - Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_geojson) for
 *     the resources *Landing Page*, *Conformance Declaration*, *Feature Collections*, and *Feature
 *     Collection*.
 * @conformanceDe Der Baustein implementiert für die Ressourcen *Landing Page*, *Conformance
 *     Declaration*, *Feature Collections* und *Feature Collection* alle Vorgaben der
 *     Konformitätsklasse "HTML" von [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html.0#rc_geojson).
 * @ref:cfg {@link de.ii.ogcapi.html.domain.HtmlConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.html.domain.ImmutableHtmlConfiguration}
 */
@Singleton
@AutoBind
public class HtmlBuildingBlock implements ApiBuildingBlock {

  private final Map<String, AuthProvider> providers;

  @Inject
  public HtmlBuildingBlock(AppContext appContext) {
    this.providers = appContext.getConfiguration().getAuth().getProviders();
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableHtmlConfiguration.Builder()
        .enabled(true)
        .noIndexEnabled(true)
        .schemaOrgEnabled(true)
        .collectionDescriptionsInOverview(false)
        .suppressEmptyCollectionsInOverview(false)
        .suppressUnusedCodelistsInOverview(false)
        .sendEtags(false)
        .legalName("Legal notice")
        .legalUrl("")
        .privacyName("Privacy notice")
        .privacyUrl("")
        .basemapUrl("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png")
        .basemapAttribution(
            "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors")
        .defaultStyle("NONE")
        .footerText("")
        .build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    Optional<HtmlConfiguration> htmlConfiguration =
        api.getData()
            .getExtension(HtmlConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled);

    if (htmlConfiguration.isPresent()
        && Objects.nonNull(htmlConfiguration.get().getLoginProvider())) {
      String loginProvider = htmlConfiguration.get().getLoginProvider();
      if (!providers.containsKey(loginProvider)) {
        return ImmutableValidationResult.builder()
            .mode(apiValidation)
            .addErrors(String.format("Could not find login provider: %s", loginProvider))
            .build();
      }
      if (!(providers.get(loginProvider) instanceof LoginProvider)) {
        return ImmutableValidationResult.builder()
            .mode(apiValidation)
            .addErrors(
                String.format("Given provider does not have login capabilities: %s", loginProvider))
            .build();
      }
      if (!Objects.equals(
          providers.get(loginProvider),
          providers.values().stream()
              .filter(p -> p instanceof IdentityProvider)
              .findFirst()
              .get())) {
        return ImmutableValidationResult.builder()
            .mode(apiValidation)
            .addErrors(
                String.format(
                    "Given login provider is not the first defined identity provider: %s",
                    loginProvider))
            .build();
      }
    }

    return ValidationResult.of();
  }
}
