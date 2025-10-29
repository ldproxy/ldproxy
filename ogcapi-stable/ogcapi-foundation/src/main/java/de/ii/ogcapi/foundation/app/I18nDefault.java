/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.I18n;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class I18nDefault implements I18n {

  private static final Map<Locale, ResourceBundle> LOCALE_RESOURCE_BUNDLE_MAP =
      ImmutableMap.of(
          Locale.ENGLISH, ResourceBundle.getBundle("i18n-en"),
          Locale.GERMAN, ResourceBundle.getBundle("i18n-de"));

  @Inject
  public I18nDefault() {}

  @Override
  public String get(String key) {
    return get(key, Optional.of(Locale.ENGLISH));
  }

  @Override
  public String get(String key, Optional<Locale> language) {
    try {
      return LOCALE_RESOURCE_BUNDLE_MAP.get(language.orElse(Locale.ENGLISH)).getString(key);
    } catch (MissingResourceException ex) {
      // just return the key
      return key;
    }
  }

  @Override
  public Set<String> getKeys() {
    return LOCALE_RESOURCE_BUNDLE_MAP.get(Locale.ENGLISH).keySet();
  }

  @Override
  public Set<String> getKeysWithPrefix(String prefix) {
    return getKeys().stream().filter(key -> key.startsWith(prefix)).collect(Collectors.toSet());
  }
}
