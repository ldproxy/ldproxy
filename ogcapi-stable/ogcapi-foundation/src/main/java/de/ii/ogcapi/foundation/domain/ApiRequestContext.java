/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.WebContext;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.Request;
import org.immutables.value.Value;

public interface ApiRequestContext {

  Splitter PATH_SPLITTER = Splitter.on('/').trimResults().omitEmptyStrings();

  WebContext getWebContext();

  ApiMediaType getMediaType();

  List<ApiMediaType> getAlternateMediaTypes();

  Optional<Locale> getLanguage();

  OgcApi getApi();

  URICustomizer getUriCustomizer();

  URICustomizer getBaseUriCustomizer();

  Optional<Request> getRequest();

  Optional<User> getUser();

  QueryParameterSet getQueryParameterSet();

  @Value.Default
  default Map<String, String> getParameters() {
    return getUriCustomizer().getQueryParams().stream()
        .map(nameValuePair -> Map.entry(nameValuePair.getName(), nameValuePair.getValue()))
        // Currently, the OGC API standards do not make use of query parameters with explode=true.
        // If that changes in the future, this method needs to return a multimap instead
        .collect(
            ImmutableMap.toImmutableMap(
                Map.Entry::getKey, Map.Entry::getValue, (value1, value2) -> value1));
  }

  @Value.Default
  default int getMaxResponseLinkHeaderSize() {
    return 2048;
  }

  List<String> getBasePathSegments();

  @Value.Derived
  default String getBasePath() {
    return Objects.requireNonNullElse(getBaseUriCustomizer().getPath(), "");
  }

  @Value.Derived
  default String getApiUri() {
    return getBaseUriCustomizer()
        .copy()
        .appendPathSegments(getApi().getData().getSubPath().toArray(new String[0]))
        .clearParameters()
        .toString();
  }

  @Value.Derived
  default String getApiPath() {
    return getBaseUriCustomizer()
        .copy()
        .appendPathSegments(getApi().getData().getSubPath().toArray(new String[0]))
        .getPath();
  }

  @Value.Derived
  default String getPath() {
    return Objects.requireNonNullElse(
        getUriCustomizer().copy().replaceInPath(getApiPath(), "").getPath(), "");
  }

  @Value.Derived
  default String getFullPath() {
    return Path.of("/", getApi().getData().getSubPath().toArray(new String[0]))
        .resolve(
            Objects.isNull(getPath()) || getPath().isEmpty()
                ? Path.of("")
                : Path.of("/").relativize(Path.of(getPath())))
        .toString();
  }

  @Value.Derived
  default Optional<String> getCollectionId() {
    String apiPath = getApiPath();
    List<String> pathSegments =
        getUriCustomizer().copy().replaceInPath(apiPath, "").getPathSegments();

    if (pathSegments.size() > 1 && Objects.equals(pathSegments.get(0), "collections")) {
      return Optional.ofNullable(pathSegments.get(1));
    }

    return Optional.empty();
  }

  @Value.Derived
  default String getMethod() {
    return getRequest().isPresent() ? getRequest().get().getMethod() : "INTERNAL";
  }
}
