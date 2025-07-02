/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.List;

@AutoMultiBind
public interface JsonSchemaExtension extends ApiExtension {

  JsonSchema process(
      JsonSchema jsonSchema,
      FeatureSchema featureSchema,
      OgcApiDataV2 apiData,
      String collectionId,
      List<Profile> profiles);
}
