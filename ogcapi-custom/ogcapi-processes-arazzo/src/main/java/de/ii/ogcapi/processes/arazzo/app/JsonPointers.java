/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JSON Pointers (RFC 6901) on JSON values as maps, lists and scalars. */
final class JsonPointers {

  private JsonPointers() {}

  /**
   * @return the value at the pointer, or null if there is none
   * @throws IllegalArgumentException if the pointer is not a JSON Pointer
   */
  static Object get(Object root, String pointer) {
    Object current = root;
    for (String token : tokens(pointer)) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(token);
      } else if (current instanceof List<?> list) {
        int index = index(token);
        if (index < 0 || index >= list.size()) {
          return null;
        }
        current = list.get(index);
      } else {
        return null;
      }
    }
    return current;
  }

  /**
   * Sets the value at the pointer; the parent of the target must exist, the maps and lists must be
   * mutable. The token {@code -} appends to a list.
   *
   * @throws IllegalArgumentException if the pointer is invalid or the parent does not exist
   */
  @SuppressWarnings("unchecked")
  static void set(Object root, String pointer, Object value) {
    List<String> tokens = tokens(pointer);
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("The target of a replacement must not be the payload.");
    }
    Object parent = get(root, toPointer(tokens.subList(0, tokens.size() - 1)));
    String last = tokens.get(tokens.size() - 1);
    if (parent instanceof Map<?, ?> map) {
      ((Map<String, Object>) map).put(last, value);
    } else if (parent instanceof List<?> list) {
      List<Object> values = (List<Object>) list;
      if ("-".equals(last)) {
        values.add(value);
      } else {
        int index = index(last);
        if (index < 0 || index >= values.size()) {
          throw new IllegalArgumentException(
              String.format("The target '%s' of a replacement does not exist.", pointer));
        }
        values.set(index, value);
      }
    } else {
      throw new IllegalArgumentException(
          String.format("The target '%s' of a replacement does not exist.", pointer));
    }
  }

  static List<String> tokens(String pointer) {
    if (pointer.isEmpty()) {
      return List.of();
    }
    if (pointer.charAt(0) != '/') {
      throw new IllegalArgumentException(
          String.format("'%s' is not a JSON Pointer, it must start with '/'.", pointer));
    }
    List<String> tokens = new ArrayList<>();
    for (String token : pointer.substring(1).split("/", -1)) {
      tokens.add(token.replace("~1", "/").replace("~0", "~"));
    }
    return tokens;
  }

  private static String toPointer(List<String> tokens) {
    StringBuilder pointer = new StringBuilder();
    for (String token : tokens) {
      pointer.append('/').append(token.replace("~", "~0").replace("/", "~1"));
    }
    return pointer.toString();
  }

  private static int index(String token) {
    if (token.isEmpty() || !token.chars().allMatch(Character::isDigit) || token.length() > 9) {
      return -1;
    }
    return Integer.parseInt(token);
  }
}
