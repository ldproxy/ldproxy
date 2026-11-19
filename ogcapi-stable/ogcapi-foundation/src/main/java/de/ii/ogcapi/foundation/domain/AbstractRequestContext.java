/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import de.ii.xtraplatform.web.domain.ForwardedUri;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Request;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class AbstractRequestContext implements ApiRequestContext {
  // RFC 3986: unreserved, sub-delims, ':', '@', '/', '?', and '%' of an escape sequence
  private static final String QUERY_CHARACTERS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=:@/?%";
  private static final String HEX = "0123456789ABCDEF";

  abstract ContainerRequestContext getRequestContext();

  /**
   * @return the query of the request as sent by the client, if known; the query of the request URI
   *     of JAX-RS is encoded again and writes a comma that separates the items of a list like a
   *     comma within an item (%2C), so the links derived from it would not be the same request
   */
  abstract Optional<String> getRawQuery();

  @Value.Derived
  @Override
  public Optional<Request> getRequest() {
    return Optional.ofNullable(getRequestContext().getRequest());
  }

  @Value.Derived
  @Override
  public Optional<String> getRequestId() {
    Object requestIdObject = getRequestContext().getProperty("REQUEST_ID");
    if (requestIdObject instanceof String requestId) {
      return Optional.of(requestId);
    }

    return Optional.empty();
  }

  @Value.Derived
  @Override
  public List<String> getBasePathSegments() {
    return ForwardedUri.prefix(getRequestContext(), getWebContext());
  }

  @Value.Derived
  @Override
  public URICustomizer getUriCustomizer() {
    URICustomizer uriCustomizer = ForwardedUri.from(getRequestContext(), getWebContext());
    return getRawQuery()
        .flatMap(rawQuery -> withQuery(uriCustomizer, rawQuery))
        .orElse(uriCustomizer);
  }

  // the URI with another query; a character that is not allowed in a query is encoded, a query
  // with an invalid escape sequence is not used
  static Optional<URICustomizer> withQuery(URICustomizer uriCustomizer, String rawQuery) {
    String uri = uriCustomizer.copy().removeQuery().toString();
    try {
      return Optional.of(new URICustomizer(uri + '?' + encodeNotAllowed(rawQuery)));
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }

  private static String encodeNotAllowed(String rawQuery) {
    StringBuilder query = new StringBuilder(rawQuery.length());
    for (byte b : rawQuery.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (b & 0xFF);
      if (c < 0x80 && QUERY_CHARACTERS.indexOf(c) >= 0) {
        query.append(c);
      } else {
        query.append('%').append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 0xF));
      }
    }
    return query.toString();
  }

  @Value.Derived
  @Override
  public URICustomizer getBaseUriCustomizer() {
    return ForwardedUri.base(getRequestContext(), getWebContext());
  }
}
