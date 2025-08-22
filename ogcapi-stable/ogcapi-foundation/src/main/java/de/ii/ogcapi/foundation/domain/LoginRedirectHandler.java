/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.xtraplatform.auth.domain.SplitCookie;
import de.ii.xtraplatform.web.domain.LoginHandler;
import io.dropwizard.auth.oauth.OAuthCredentialAuthFilter;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.apache.hc.core5.net.URIBuilder;

@AutoMultiBind
public interface LoginRedirectHandler extends ApiExtension {
  boolean isEnabledFor(OgcApiDataV2 data, ApiMediaType mediaType);

  Optional<String> getLoginProvider(OgcApiDataV2 data);

  default Optional<Response> redirect(ApiRequestContext requestContext, Set<String> activeScopes) {
    if (isEnabledFor(requestContext.getApi().getData(), requestContext.getMediaType())) {
      URI loginUri = getLoginUri(requestContext, activeScopes);
      List<String> authCookies =
          SplitCookie.deleteToken(loginUri.getHost(), Objects.equals(loginUri, "https"));

      ResponseBuilder response = Response.seeOther(loginUri);
      authCookies.forEach(cookie -> response.header("Set-Cookie", cookie));

      return Optional.of(response.build());
    }
    return Optional.empty();
  }

  default URI getLoginUri(ApiRequestContext requestContext, Set<String> activeScopes) {
    URIBuilder uriBuilder =
        requestContext
            .getBaseUriCustomizer()
            .appendPath(LoginHandler.PATH_LOGIN)
            .addParameter(
                LoginHandler.PARAM_LOGIN_REDIRECT_URI,
                requestContext
                    .getUriCustomizer()
                    .removeParameter(OAuthCredentialAuthFilter.OAUTH_ACCESS_TOKEN_PARAM)
                    .toString());

    if (!activeScopes.isEmpty()) {
      uriBuilder.addParameter(LoginHandler.PARAM_LOGIN_SCOPES, String.join(" ", activeScopes));
    }

    // TODO: the parameter is currently ignored since only one provider is supported
    Optional<String> loginProvider = getLoginProvider(requestContext.getApi().getData());
    if (loginProvider.isPresent()) {
      uriBuilder.addParameter("provider_id", loginProvider.get());
    }

    return URI.create(uriBuilder.optimize().toString());
  }
}
