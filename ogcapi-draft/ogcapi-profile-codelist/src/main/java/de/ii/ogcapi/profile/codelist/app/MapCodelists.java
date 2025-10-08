/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.codelist.app;

import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaVisitor;
import de.ii.ogcapi.foundation.domain.Profile;
import java.util.List;

public class MapCodelists implements JsonSchemaVisitor {

  private final List<Profile> profiles;

  public MapCodelists(List<Profile> profiles) {
    this.profiles = profiles;
  }

  @Override
  public JsonSchema visit(JsonSchema schema) {
    if (schema.getCodelistId().isPresent()) {
      String codelistId = schema.getCodelistId().get();
      return profiles.stream()
          .filter(profile -> profile instanceof ProfileCodelist)
          .findFirst()
          .map(p -> ((ProfileCodelist) p).process(schema, codelistId))
          .orElse(schema);
    }

    return visitProperties(schema);
  }
}
