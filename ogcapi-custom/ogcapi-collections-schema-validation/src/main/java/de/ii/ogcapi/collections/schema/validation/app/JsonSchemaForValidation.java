/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.validation.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaExtension;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonSchemaForValidation implements JsonSchemaExtension {

  @Inject
  public JsonSchemaForValidation() {}

  @Override
  public int getPriority() {
    return 100;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SchemaValidationConfiguration.class;
  }

  @Override
  public JsonSchema process(
      JsonSchema jsonSchema,
      FeatureSchema featureSchema,
      OgcApiDataV2 apiData,
      String collectionId,
      List<Profile> profiles) {
    return profiles.stream()
        .filter(profile -> profile instanceof ProfileJsonSchemaForValidation)
        .findFirst()
        .map(profile -> (ProfileJsonSchemaForValidation) profile)
        .map(
            profile ->
                jsonSchema.accept(new ForValidation(profile)).accept(new CleanupForValidation()))
        .orElse(jsonSchema);
  }
}
