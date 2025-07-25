/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import java.util.List;

@AutoMultiBind
public interface SchemaFormatExtension extends FormatExtension {

  @Override
  default Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SchemaConfiguration.class;
  }

  @Override
  default boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return FormatExtension.super.isEnabledForApi(apiData);
  }

  @Override
  default boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return FormatExtension.super.isEnabledForApi(apiData, collectionId);
  }

  Object getEntity(
      JsonSchemaDocument schema,
      List<Link> links,
      String collectionId,
      OgcApi api,
      ApiRequestContext requestContext);
}
