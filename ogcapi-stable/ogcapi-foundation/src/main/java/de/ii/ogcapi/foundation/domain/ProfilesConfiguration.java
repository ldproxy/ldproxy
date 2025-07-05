/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public interface ProfilesConfiguration {

  Map<String, String> getDefaultProfiles();

  default ProfilesConfiguration mergeInto(ProfilesConfiguration source) {
    Map<String, String> mergedDefaultProfiles = new LinkedHashMap<>(getDefaultProfiles());

    source
        .getDefaultProfiles()
        .forEach(
            (profileSet, profile) -> {
              if (!mergedDefaultProfiles.containsKey(profileSet)) {
                mergedDefaultProfiles.put(profileSet, profile);
              }
            });

    return () -> mergedDefaultProfiles;
  }
}
