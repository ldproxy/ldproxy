/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.domain;

import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import jakarta.ws.rs.core.Response;

public interface ResourceFormatExtension extends FormatExtension {

  @Override
  default boolean canSupportTransactions() {
    return true;
  }

  Object getResourceEntity(
      byte[] resource, String resourceId, OgcApiDataV2 apiData, ApiRequestContext requestContext);

  Response putResource(
      byte[] resource, String resourceId, OgcApiDataV2 apiData, ApiRequestContext requestContext);

  @Override
  default Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ResourcesConfiguration.class;
  }

  @Override
  default boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return FormatExtension.super.isEnabledForApi(apiData);
  }
}
