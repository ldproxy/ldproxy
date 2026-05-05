/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import jakarta.ws.rs.NotFoundException;

public abstract class EndpointRequiresFeatures extends Endpoint {

  public EndpointRequiresFeatures(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  protected static void ensureSupportForFeatures(OgcApiDataV2 apiData) {
    apiData
        .getExtension(FeaturesCoreConfiguration.class)
        .filter(ExtensionConfiguration::isEnabled)
        .filter(
            cfg ->
                cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                    != FeaturesCoreConfiguration.ItemType.unknown)
        .orElseThrow(() -> new NotFoundException("Features are not supported for this API."));
  }
}
