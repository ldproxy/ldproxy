/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** RFC 7240 {@code Prefer} header parsing helpers used by {@link EndpointTransactions}. */
final class PreferHeader {

  enum PreferReturn {
    NONE("none"),
    MINIMAL("minimal"),
    REPRESENTATION("representation");

    private final String header;

    PreferReturn(String header) {
      this.header = header;
    }

    String headerValue() {
      return header;
    }

    static Optional<PreferReturn> fromHeader(String value) {
      if (value == null) return Optional.empty();
      String v = value.trim().toLowerCase(Locale.ROOT);
      for (PreferReturn r : values()) {
        if (r.header.equals(v)) return Optional.of(r);
      }
      return Optional.empty();
    }
  }

  private PreferHeader() {}

  static PreferReturn parseReturn(List<String> preferHeaders, PreferReturn fallback) {
    if (preferHeaders == null) return fallback;
    for (String header : preferHeaders) {
      for (String token : header.split(",")) {
        String t = token.trim();
        if (t.regionMatches(true, 0, "return", 0, "return".length())) {
          int eq = t.indexOf('=');
          if (eq > 0) {
            Optional<PreferReturn> r = PreferReturn.fromHeader(t.substring(eq + 1).trim());
            if (r.isPresent()) return r.get();
          }
        }
      }
    }
    return fallback;
  }

  static boolean containsPreferToken(List<String> preferHeaders, String token) {
    if (preferHeaders == null) return false;
    for (String header : preferHeaders) {
      for (String t : header.split(",")) {
        if (token.equalsIgnoreCase(t.trim())) return true;
      }
    }
    return false;
  }
}
