/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public interface I18n {

  Set<Locale> getLanguages();

  String get(String key);

  String get(String key, Optional<Locale> language);

  Set<String> getKeys();

  Set<String> getKeysWithPrefix(String prefix);

  /**
   * @param rel a link relation type, e.g. {@code version-history}
   * @param language the language of the title
   * @return the title of a link with the relation type, if there is a translation (key {@code
   *     versionHistoryLink})
   */
  default Optional<String> getLinkTitle(String rel, Optional<Locale> language) {
    String key = getLinkTitleKey(rel);
    String title = get(key, language);
    // a missing translation is returned as the key
    return title.equals(key) ? Optional.empty() : Optional.of(title);
  }

  private static String getLinkTitleKey(String rel) {
    String[] parts = rel.split("-");
    StringBuilder key = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      if (!parts[i].isEmpty()) {
        key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
      }
    }
    return key.append("Link").toString();
  }
}
