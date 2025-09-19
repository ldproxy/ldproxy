/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.domain;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiSecurity;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.web.domain.LoginHandler;
import de.ii.xtraplatform.web.domain.StaticResourceHandler;
import de.ii.xtraplatform.web.domain.URICustomizer;
import io.dropwizard.views.common.View;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

public abstract class OgcApiView extends View {

  public abstract URICustomizer uriCustomizer();

  @Nullable
  public abstract HtmlConfiguration htmlConfig();

  public abstract String basePath();

  public abstract String apiPath();

  @Value.Derived
  public String assetsPrefix() {
    return apiPath() + StaticResourceHandler.PREFIX;
  }

  @Value.Default
  public boolean noIndex() {
    return Optional.ofNullable(htmlConfig()).map(HtmlConfiguration::getNoIndexEnabled).orElse(true);
  }

  @Nullable
  public abstract List<NavigationDTO> breadCrumbs();

  @Value.Default
  public List<Link> rawLinks() {
    return ImmutableList.of();
  }

  // Constructor Variables as new Member

  @Nullable
  public abstract String title();

  @Nullable
  public abstract String description();

  @Nullable
  public abstract OgcApiDataV2 apiData();

  public abstract Optional<? extends Principal> user();

  @Value.Derived
  public boolean hasLoginProvider() {
    return Optional.ofNullable(apiData())
            .flatMap(OgcApiDataV2::getAccessControl)
            .filter(ApiSecurity::isEnabled)
            .isPresent()
        && Optional.ofNullable(htmlConfig())
            .filter(htmlConfiguration -> Objects.nonNull(htmlConfiguration.getLoginProvider()))
            .isPresent();
  }

  @Value.Derived
  public boolean hasEmptyScopes() {
    return Optional.ofNullable(apiData())
        .flatMap(OgcApiDataV2::getAccessControl)
        .filter(apiSecurity -> apiSecurity.getScopes().isEmpty())
        .isPresent();
  }

  @Value.Derived
  public Optional<String> logoutUri() {
    return hasLoginProvider() && user().isPresent()
        ? Optional.of(
            uriCustomizer()
                .copy()
                .setPath(basePath())
                .appendPath(LoginHandler.PATH_LOGOUT)
                .clearParameters()
                .addParameter(
                    LoginHandler.PARAM_LOGOUT_REDIRECT_URI,
                    uriCustomizer().copy().setPath(apiPath()).clearParameters().toString())
                .toString())
        : Optional.empty();
  }

  @Value.Derived
  public Optional<String> loginUri() {
    // TODO: to show the login when scopes are enabled, ApiSecurityInfo.getActiveScopes would be
    // needed here
    return hasLoginProvider() && user().isEmpty() && hasEmptyScopes()
        ? Optional.of(
            uriCustomizer()
                .copy()
                .setPath(basePath())
                .appendPath(LoginHandler.PATH_LOGIN)
                .clearParameters()
                .addParameter(LoginHandler.PARAM_LOGIN_REDIRECT_URI, uriCustomizer().toString())
                .toString())
        : Optional.empty();
  }

  public OgcApiView(String templateName) {
    super(String.format("/templates/%s", templateName), Charsets.UTF_8);
  }

  public List<NavigationDTO> getFormats() {
    return rawLinks().stream()
        .filter(
            link -> Objects.equals(link.getRel(), "alternate") && !link.getTypeLabel().isBlank())
        .sorted(Comparator.comparing(link -> link.getTypeLabel().toUpperCase()))
        .map(link -> new NavigationDTO(link.getTypeLabel(), link.getHref()))
        .collect(Collectors.toList());
  }

  public boolean hasBreadCrumbs() {
    if (breadCrumbs() != null) {
      return breadCrumbs().size() > 1;
    }
    return false;
  }

  public String getBreadCrumbsList() {
    String result = "";
    for (int i = 0; i < breadCrumbs().size(); i++) {
      NavigationDTO item = breadCrumbs().get(i);
      result +=
          "{ \"@type\": \"ListItem\", \"position\": "
              + (i + 1)
              + ", \"name\": \""
              + item.label
              + "\"";
      if (Objects.nonNull(item.url)) {
        result += ", \"item\": \"" + item.url + "\"";
      }
      result += " }";
      if (i < breadCrumbs().size() - 1) {
        result += ",\n    ";
      }
    }
    return result;
  }

  public String getAttribution() {
    if (Objects.isNull(htmlConfig())) {
      return null;
    }
    return htmlConfig().getBasemapAttribution();
  }
}
