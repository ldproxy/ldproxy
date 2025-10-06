/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.validation.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileValidationReceivablesGeoJson extends ProfileValidationReceivables {

  @Inject
  ProfileValidationReceivablesGeoJson(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry, providers);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && apiData
            .getExtension(GeoJsonConfiguration.class, collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(false);
  }

  @Override
  public String getId() {
    return "validation-receivables-geojson";
  }

  @Override
  public String getLabel() {
    return "JSON Schema for Validation (Receivables, GeoJSON)";
  }
}
