/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.google.common.collect.ImmutableList;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public abstract class HeaderPrefer extends ApiExtensionCache implements ApiHeader {

  /**
   * Standard {@code handling=…} preference values defined by RFC 7240 §4.4. Used by endpoints that
   * gate request-body validation on {@code Prefer: handling=strict}.
   */
  public enum Handling {
    STRICT("strict"),
    LENIENT("lenient");

    private final String header;

    Handling(String header) {
      this.header = header;
    }

    public String headerValue() {
      return header;
    }

    public static Optional<Handling> fromHeader(String value) {
      if (value == null) return Optional.empty();
      String v = value.trim().toLowerCase(Locale.ROOT);
      for (Handling h : values()) {
        if (h.header.equals(v)) return Optional.of(h);
      }
      return Optional.empty();
    }
  }

  /**
   * Standard {@code return=…} preference values defined by RFC 7240 §4.2. Used by endpoints that
   * let the client opt into a minimal response ({@code return=minimal}) or no response body ({@code
   * return=none}) instead of the full default representation.
   */
  public enum Return {
    NONE("none"),
    MINIMAL("minimal"),
    REPRESENTATION("representation");

    private final String header;

    Return(String header) {
      this.header = header;
    }

    public String headerValue() {
      return header;
    }

    public static Optional<Return> fromHeader(String value) {
      if (value == null) return Optional.empty();
      String v = value.trim().toLowerCase(Locale.ROOT);
      for (Return r : values()) {
        if (r.header.equals(v)) return Optional.of(r);
      }
      return Optional.empty();
    }
  }

  private final Schema<?> schema =
      new StringSchema()
          ._enum(ImmutableList.of("handling=strict", "handling=lenient"))
          ._default("handling=lenient");
  protected final SchemaValidator schemaValidator;

  protected HeaderPrefer(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getName() {
    return "Prefer";
  }

  @Override
  public boolean isRequestHeader() {
    return true;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  /**
   * Parses the {@code handling=…} preference from a list of {@code Prefer} header values.
   *
   * @param preferHeaders raw header values (multiple comma-separated tokens per value allowed; may
   *     be {@code null})
   * @param fallback returned when no recognised value is present
   * @return the parsed value, or {@code fallback} when none was recognised or when conflicting
   *     values were supplied (RFC 7240: ambiguous preferences are treated as if not specified)
   */
  public static Handling parseHandling(List<String> preferHeaders, Handling fallback) {
    return parseParameterised(preferHeaders, "handling", Handling::fromHeader, fallback);
  }

  /**
   * Parses the {@code return=…} preference from a list of {@code Prefer} header values. Same
   * conflict semantics as {@link #parseHandling}: ambiguous (multiple distinct) values collapse to
   * {@code fallback} per RFC 7240 §2.
   */
  public static Return parseReturn(List<String> preferHeaders, Return fallback) {
    return parseParameterised(preferHeaders, "return", Return::fromHeader, fallback);
  }

  /**
   * @return {@code true} if any of the {@code Prefer} header values contains the unparameterised
   *     token (e.g. {@code respond-async}). Matching is case-insensitive and exact: substrings
   *     ({@code respond-async-xyz}) and parameterised values ({@code return=respond-async}) do not
   *     match.
   */
  public static boolean containsToken(List<String> preferHeaders, String token) {
    if (preferHeaders == null) return false;
    for (String header : preferHeaders) {
      for (String t : header.split(",")) {
        if (token.equalsIgnoreCase(t.trim())) return true;
      }
    }
    return false;
  }

  /**
   * Generic parser for a parameterised RFC 7240 preference (e.g. {@code handling=strict}, {@code
   * return=minimal}).
   *
   * <p>Scans every comma-separated token in every header value, decodes any whose parameter name
   * matches {@code name} (case-insensitively), and collects the distinct successfully-parsed
   * values. Returns the single value when exactly one was found; returns {@code fallback} otherwise
   * — including the RFC 7240 §2 case where mutually-exclusive preferences are sent together ("a
   * request containing both preferences can be treated as though neither were specified").
   */
  public static <T> T parseParameterised(
      List<String> preferHeaders, String name, Function<String, Optional<T>> parser, T fallback) {
    if (preferHeaders == null) return fallback;
    Set<T> found = new HashSet<>();
    for (String header : preferHeaders) {
      for (String token : header.split(",")) {
        String t = token.trim();
        if (t.regionMatches(true, 0, name, 0, name.length())) {
          int eq = t.indexOf('=');
          if (eq > 0 && t.substring(name.length(), eq).trim().isEmpty()) {
            parser.apply(t.substring(eq + 1).trim()).ifPresent(found::add);
          }
        }
      }
    }
    return found.size() == 1 ? found.iterator().next() : fallback;
  }
}
