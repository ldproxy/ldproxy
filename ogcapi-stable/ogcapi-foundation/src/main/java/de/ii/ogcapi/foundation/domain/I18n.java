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
}
