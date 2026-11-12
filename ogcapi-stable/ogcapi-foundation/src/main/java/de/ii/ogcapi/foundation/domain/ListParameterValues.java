/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.google.common.base.Splitter;
import java.util.List;
import java.util.regex.Pattern;
import org.glassfish.jersey.uri.UriComponent;

/**
 * The value of a query parameter with a list of values (style form, explode false) is a
 * comma-separated list. A comma within an item is percent-encoded ({@code %2C}), a comma that
 * separates items is not. Since the encoding is lost when the value is decoded, the value of such a
 * parameter is decoded with {@link #decode(String)}, which keeps an encoded comma as a marker, and
 * split into items with {@link #split(String)}, which restores the comma in the items.
 */
public final class ListParameterValues {

  /** Replaces an encoded comma in a decoded value, until the value is split into its items. */
  public static final char ENCODED_COMMA = '\u001F';

  private static final Pattern ENCODED_COMMA_PATTERN = Pattern.compile("%2[cC]");
  private static final String ENCODED_MARKER = "%1F";
  private static final Splitter ITEM_SPLITTER = Splitter.on(',').trimResults();

  private ListParameterValues() {}

  /**
   * @param rawValue the value of the query parameter as it is in the request, still encoded
   * @return the decoded value, an encoded comma is represented by {@link #ENCODED_COMMA}
   */
  public static String decode(String rawValue) {
    return UriComponent.decode(
        ENCODED_COMMA_PATTERN.matcher(rawValue).replaceAll(ENCODED_MARKER),
        UriComponent.Type.QUERY_PARAM);
  }

  /**
   * @param value the decoded value of the query parameter
   * @return the items of the list, an empty value is an empty list
   */
  public static List<String> split(String value) {
    if (value.isBlank()) {
      return List.of();
    }
    return ITEM_SPLITTER.splitToStream(value).map(ListParameterValues::restoreCommas).toList();
  }

  private static String restoreCommas(String item) {
    return item.replace(ENCODED_COMMA, ',');
  }
}
