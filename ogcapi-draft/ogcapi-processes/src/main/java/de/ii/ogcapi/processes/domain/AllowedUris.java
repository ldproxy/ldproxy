/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The URIs to which the server sends requests on behalf of a client, e.g. the callbacks of an
 * asynchronous execution or the requests of a workflow to an external API. A URI is allowed, if it
 * has the scheme, host and port of an entry and its path starts with the path of the entry; the
 * entry {@code *} allows every HTTP(S) URI. Otherwise a client could direct requests of the server
 * to any host the server can reach, including services that are only reachable from the server.
 */
public final class AllowedUris {

  /** The entry that allows every HTTP(S) URI, for tests or a closed environment. */
  public static final String ANY = "*";

  private AllowedUris() {}

  /**
   * @param uri the URI of a request
   * @param allowedUris the entries
   * @return whether one of the entries allows the URI
   */
  public static boolean isAllowed(String uri, List<String> allowedUris) {
    try {
      URI normalized = new URI(uri).normalize();
      return allowedUris.stream().anyMatch(allowed -> isBelow(normalized, allowed));
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static boolean isBelow(URI uri, String allowedUri) {
    if (ANY.equals(allowedUri)) {
      return Objects.nonNull(uri.getHost())
          && ("http".equalsIgnoreCase(uri.getScheme())
              || "https".equalsIgnoreCase(uri.getScheme()));
    }
    try {
      URI allowed = new URI(allowedUri).normalize();
      return Objects.nonNull(allowed.getScheme())
          && Objects.nonNull(allowed.getHost())
          && allowed.getScheme().equalsIgnoreCase(uri.getScheme())
          && allowed.getHost().equalsIgnoreCase(uri.getHost())
          && port(allowed) == port(uri)
          && isPathBelow(
              Objects.requireNonNullElse(uri.getRawPath(), ""),
              Objects.requireNonNullElse(allowed.getRawPath(), ""));
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static int port(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  // the path of the allowed URI ends at a segment boundary, "/callbacks" does not accept
  // "/callbacks-other"; encoded dots and slashes could be decoded to other segments by the
  // receiving server, so such a path is not below a path
  private static boolean isPathBelow(String path, String allowedPath) {
    if (allowedPath.isEmpty() || "/".equals(allowedPath)) {
      return true;
    }
    String lowerCase = path.toLowerCase(Locale.ROOT);
    if (lowerCase.contains("%2e") || lowerCase.contains("%2f")) {
      return false;
    }
    return path.equals(allowedPath)
        || path.startsWith(allowedPath.endsWith("/") ? allowedPath : allowedPath + "/");
  }
}
