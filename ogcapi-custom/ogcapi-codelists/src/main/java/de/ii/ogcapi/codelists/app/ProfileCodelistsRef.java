/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.codelists.domain.CodelistsConfiguration;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaInteger;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaString;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaInteger;
import de.ii.ogcapi.features.core.domain.JsonSchemaString;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileCodelistsRef extends ProfileCodelist {

  @Inject
  ProfileCodelistsRef(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  @Override
  public String getId() {
    return "codelists-ref";
  }

  @Override
  public JsonSchema process(JsonSchema schema, String codelistId, Optional<String> codelistUri) {
    if (codelistUri.isEmpty()) {
      return schema;
    }

    if (schema instanceof JsonSchemaString) {
      return new ImmutableJsonSchemaString.Builder()
          .from((JsonSchemaString) schema)
          .codelistUri(codelistUri)
          .build();
    } else if (schema instanceof JsonSchemaInteger) {
      return new ImmutableJsonSchemaInteger.Builder()
          .from((JsonSchemaInteger) schema)
          .codelistUri(codelistUri)
          .build();
    }
    return schema;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CodelistsConfiguration.class;
  }
}
