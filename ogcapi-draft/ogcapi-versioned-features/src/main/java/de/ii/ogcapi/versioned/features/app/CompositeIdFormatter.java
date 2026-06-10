/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Inverse of {@link VersionedMutationStrategy#splitCompositeId}: given a canonical id and the
 * version's {@code PRIMARY_INTERVAL_START}, produces the composite id that the configured {@code
 * compositeIdPattern} would split back into the inputs.
 *
 * <p>The pattern is a regex with two named groups {@code id} and {@code start}. The literal text
 * between the groups (and any literal prefix/suffix outside {@code ^}/{@code $}) becomes the join
 * template. The captured timestamp suffix is formatted via {@code compositeIdTimestampFormat}
 * (default {@code yyyyMMdd'T'HHmmss'Z'}).
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Pattern {@code ^(?<id>DE[A-Za-z0-9]{14})(?<start>\d{8}T\d{6}Z)$}, default format → {@code
 *       DEHE862010005DDo} + {@code 2009-10-16T07:56:37Z} → {@code
 *       DEHE862010005DDo20091016T075637Z}.
 *   <li>Pattern {@code ^(?<id>.+?)\.(?<start>\d{8}T\d{6}Z)$} → {@code 1.20010702T104317Z}.
 * </ul>
 */
public final class CompositeIdFormatter {

  public static final String DEFAULT_TIMESTAMP_FORMAT = "yyyyMMdd'T'HHmmss'Z'";

  private CompositeIdFormatter() {}

  /** Returns the composite id, or {@code canonical} when no pattern is configured. */
  public static String format(
      String pattern, String timestampFormat, String canonical, Instant start) {
    if (Objects.isNull(pattern) || pattern.isBlank() || Objects.isNull(canonical)) {
      return canonical;
    }
    if (Objects.isNull(start)) {
      return canonical;
    }
    String fmt =
        (Objects.isNull(timestampFormat) || timestampFormat.isBlank())
            ? DEFAULT_TIMESTAMP_FORMAT
            : timestampFormat;
    String formattedStart = DateTimeFormatter.ofPattern(fmt).withZone(ZoneOffset.UTC).format(start);

    Parts parts = parsePattern(pattern);
    if (parts == null) {
      return canonical;
    }
    return parts.prefix + canonical + parts.between + formattedStart + parts.suffix;
  }

  private static Parts parsePattern(String pattern) {
    int idOpen = pattern.indexOf("(?<id>");
    int startOpen = pattern.indexOf("(?<start>");
    if (idOpen < 0 || startOpen < 0 || startOpen < idOpen) {
      return null;
    }
    int idClose = findMatchingClose(pattern, idOpen);
    int startClose = findMatchingClose(pattern, startOpen);
    if (idClose < 0 || startClose < 0) {
      return null;
    }
    String before = pattern.substring(0, idOpen);
    String between = pattern.substring(idClose + 1, startOpen);
    String after = pattern.substring(startClose + 1);
    if (before.startsWith("^")) {
      before = before.substring(1);
    }
    if (after.endsWith("$")) {
      after = after.substring(0, after.length() - 1);
    }
    return new Parts(unescapeRegex(before), unescapeRegex(between), unescapeRegex(after));
  }

  // Locate the ')' that closes the group starting at `open`. Skips escaped chars (\X) and
  // character-class contents [...].
  private static int findMatchingClose(String s, int open) {
    int depth = 0;
    int i = open;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '\\') {
        i += 2;
        continue;
      }
      if (c == '[') {
        i++;
        while (i < s.length() && s.charAt(i) != ']') {
          if (s.charAt(i) == '\\') {
            i++;
          }
          i++;
        }
        i++;
        continue;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
      i++;
    }
    return -1;
  }

  // Strip regex escapes: \X → X. Sufficient for the literal prefix/middle/suffix text between
  // named groups in the composite-id pattern (no character classes, no quantifiers expected
  // there).
  private static String unescapeRegex(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        sb.append(s.charAt(i + 1));
        i++;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static final class Parts {
    final String prefix;
    final String between;
    final String suffix;

    Parts(String prefix, String between, String suffix) {
      this.prefix = prefix;
      this.between = between;
      this.suffix = suffix;
    }
  }
}
