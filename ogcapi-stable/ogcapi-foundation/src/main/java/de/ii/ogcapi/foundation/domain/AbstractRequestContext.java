/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.util.AbstractMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Request;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Immutable
public abstract class AbstractRequestContext implements ApiRequestContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRequestContext.class);
  private static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";
  public static final String STATIC_PATH = "___static___";

  abstract URI getRequestUri();

  abstract Optional<ContainerRequestContext> getRequestContext();

  abstract URI getExternalUri();

  @Override
  public abstract ApiMediaType getMediaType();

  @Override
  public abstract List<ApiMediaType> getAlternateMediaTypes();

  @Override
  public abstract Optional<Locale> getLanguage();

  @Value.Derived
  @Override
  public Optional<Request> getRequest() {
    return getRequestContext().map(ContainerRequestContext::getRequest);
  }

  @Value.Derived
  @Override
  public List<String> getExternalUriPath() {
    if (getRequestContext().isPresent()
        && getRequestContext().get().getHeaders().containsKey(X_FORWARDED_PREFIX)) {
      return PATH_SPLITTER.splitToList(
          getRequestContext().get().getHeaderString(X_FORWARDED_PREFIX));
    }

    return PATH_SPLITTER.splitToList(getExternalUri().getPath());
  }

  @Value.Derived
  @Override
  public URICustomizer getUriCustomizer() {
    return new URICustomizer(getRequestUri()).prependPathSegments(getExternalUriPath());
  }

  @Value.Derived
  @Override
  public URICustomizer getBaseUriCustomizer() {
    return new URICustomizer(getRequestUri())
        .setPathSegments(getExternalUriPath())
        .ensureNoTrailingSlash()
        .clearParameters();
  }

  @Value.Derived
  @Override
  public String getStaticUrlPrefix() {
    return getBaseUriCustomizer().ensureLastPathSegment(STATIC_PATH).getPath();
  }

  @Value.Default
  @Override
  public Map<String, String> getParameters() {
    return getUriCustomizer().getQueryParams().stream()
        .map(
            nameValuePair ->
                new AbstractMap.SimpleImmutableEntry<>(
                    nameValuePair.getName(), nameValuePair.getValue()))
        // Currently, the OGC API standards do not make use of query parameters with explode=true.
        // If that changes in the future, this method needs to return a multimap instead
        .collect(
            ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (value1, value2) -> {
                  if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                        "Duplicate parameter found, the following value is ignored: {}", value2);
                  }
                  return value1;
                }));
  }
}
