/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaType.CompatibilityLevel;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.LoginRedirectHandler;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.Objects;
import java.util.Optional;

@Singleton
@AutoBind
public class LoginRedirectHandlerHtml implements LoginRedirectHandler {

  @Inject
  LoginRedirectHandlerHtml() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return HtmlConfiguration.class;
  }

  @Override
  public boolean isEnabledFor(OgcApiDataV2 data, ApiMediaType mediaType) {
    return isEnabledForApi(data)
        && ApiMediaType.isCompatible(
            mediaType.type(), MediaType.TEXT_HTML_TYPE, CompatibilityLevel.TYPES)
        && getLoginProvider(data).isPresent();
  }

  @Override
  public Optional<String> getLoginProvider(OgcApiDataV2 data) {
    return data.getExtension(HtmlConfiguration.class)
        .filter(htmlConfiguration -> Objects.nonNull(htmlConfiguration.getLoginProvider()))
        .map(HtmlConfiguration::getLoginProvider);
  }
}
