/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One parsed segment of an {@code xmlPaths} chain. The configured segment syntax is {@code
 * [*]name([attribute=value])*}, optionally followed by a trailing {@code /}: without it the element
 * wraps the remainder of the chain (and eventually the property value); with it the element is
 * <em>empty</em> and injected into the chain at its position — written with its attributes and
 * closed immediately, as needed for constant elements like the ISO 19139 {@code valueUnit} child of
 * {@code DQ_QuantitativeResult}, which must precede the {@code value} element. Attribute values may
 * be single- or double-quoted; a {@code ]} inside a value is not supported.
 *
 * <p>A leading {@code *} marks the segment from which the chain repeats for each member of an
 * object array: the segments before it wrap the array as a whole and are written once, the segments
 * from it are written again for every member. Without the marker the whole chain repeats, which is
 * what a repeated value needs.
 */
public final class XmlPathElement {

  private final String name;
  private final Map<String, String> attributes;
  private final boolean emptyElement;
  private final boolean repeats;

  private XmlPathElement(
      String name, Map<String, String> attributes, boolean emptyElement, boolean repeats) {
    this.name = name;
    this.attributes = Collections.unmodifiableMap(attributes);
    this.emptyElement = emptyElement;
    this.repeats = repeats;
  }

  /**
   * Parses an {@code xmlPaths} chain segment. Throws {@link IllegalArgumentException} with a
   * human-readable reason on malformed input; {@code GmlConfiguration} maps that to a startup
   * failure naming the property path.
   */
  public static XmlPathElement parse(String entry) {
    String s = Objects.requireNonNullElse(entry, "").trim();
    boolean repeats = s.startsWith("*");
    if (repeats) {
      s = s.substring(1).trim();
    }
    boolean emptyElement = s.endsWith("/");
    if (emptyElement) {
      s = s.substring(0, s.length() - 1).trim();
    }
    int bracket = s.indexOf('[');
    String name = (bracket < 0 ? s : s.substring(0, bracket)).trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("the element name is empty");
    }
    if (name.contains("]") || name.contains(" ")) {
      throw new IllegalArgumentException(String.format("'%s' is not a valid element name", name));
    }
    Map<String, String> attributes = new LinkedHashMap<>();
    int pos = bracket;
    while (pos >= 0 && pos < s.length()) {
      if (s.charAt(pos) != '[') {
        throw new IllegalArgumentException(
            String.format("unexpected characters after ']': '%s'", s.substring(pos)));
      }
      int close = s.indexOf(']', pos);
      if (close < 0) {
        throw new IllegalArgumentException("unterminated '[' — expected '[attribute=value]'");
      }
      String predicate = s.substring(pos + 1, close);
      int eq = predicate.indexOf('=');
      if (eq <= 0) {
        throw new IllegalArgumentException(
            String.format("'[%s]' is not of the form '[attribute=value]'", predicate));
      }
      String attribute = predicate.substring(0, eq).trim();
      String value = unquote(predicate.substring(eq + 1).trim());
      if (attribute.isEmpty()) {
        throw new IllegalArgumentException(
            String.format("'[%s]' is not of the form '[attribute=value]'", predicate));
      }
      if (attributes.put(attribute, value) != null) {
        throw new IllegalArgumentException(String.format("duplicate attribute '%s'", attribute));
      }
      pos = close + 1;
    }
    return new XmlPathElement(name, attributes, emptyElement, repeats);
  }

  private static String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("'") && value.endsWith("'"))
            || (value.startsWith("\"") && value.endsWith("\"")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  public String getName() {
    return name;
  }

  /** Attributes in configuration order. */
  public Map<String, String> getAttributes() {
    return attributes;
  }

  /** {@code true} for injected empty elements (trailing {@code /}), which do not wrap the value. */
  public boolean isEmptyElement() {
    return emptyElement;
  }

  /** {@code true} for the segment (leading {@code *}) from which the chain repeats per member. */
  public boolean repeats() {
    return repeats;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof XmlPathElement)) {
      return false;
    }
    XmlPathElement other = (XmlPathElement) o;
    return name.equals(other.name)
        && attributes.equals(other.attributes)
        && emptyElement == other.emptyElement
        && repeats == other.repeats;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, attributes, emptyElement, repeats);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (repeats) {
      sb.append('*');
    }
    sb.append(name);
    attributes.forEach((k, v) -> sb.append('[').append(k).append('=').append(v).append(']'));
    if (emptyElement) {
      sb.append('/');
    }
    return sb.toString();
  }
}
