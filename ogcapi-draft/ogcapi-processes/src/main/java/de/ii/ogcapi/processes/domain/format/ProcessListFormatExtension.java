/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.format;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.common.domain.GenericFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.model.web.ProcessListResponse;

@AutoMultiBind
public interface ProcessListFormatExtension extends GenericFormatExtension {

  @Override
  default Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  default boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return GenericFormatExtension.super.isEnabledForApi(apiData)
        && isExtensionEnabled(apiData, FORMAT_MAP.get(getMediaType().label()));
  }

  Object getEntity(
      ProcessListResponse processListResponse, OgcApi api, ApiRequestContext requestContext);
}
