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
import java.util.function.Function;

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

  enum PreferHandling {
    STRICT("strict"),
    LENIENT("lenient");

    private final String header;

    PreferHandling(String header) {
      this.header = header;
    }

    String headerValue() {
      return header;
    }

    static Optional<PreferHandling> fromHeader(String value) {
      if (value == null) return Optional.empty();
      String v = value.trim().toLowerCase(Locale.ROOT);
      for (PreferHandling h : values()) {
        if (h.header.equals(v)) return Optional.of(h);
      }
      return Optional.empty();
    }
  }

  private PreferHeader() {}

  static PreferReturn parseReturn(List<String> preferHeaders, PreferReturn fallback) {
    return parseParameterised(preferHeaders, "return", PreferReturn::fromHeader, fallback);
  }

  static PreferHandling parseHandling(List<String> preferHeaders, PreferHandling fallback) {
    return parseParameterised(preferHeaders, "handling", PreferHandling::fromHeader, fallback);
  }

  private static <T> T parseParameterised(
      List<String> preferHeaders, String name, Function<String, Optional<T>> parser, T fallback) {
    if (preferHeaders == null) return fallback;
    for (String header : preferHeaders) {
      for (String token : header.split(",")) {
        String t = token.trim();
        if (t.regionMatches(true, 0, name, 0, name.length())) {
          int eq = t.indexOf('=');
          if (eq > 0 && t.substring(name.length(), eq).trim().isEmpty()) {
            Optional<T> r = parser.apply(t.substring(eq + 1).trim());
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
