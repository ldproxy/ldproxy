/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.geojson.domain.ProfileGeoJson;
import de.ii.ogcapi.features.geojson.domain.ProfileSetGeoJson;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.SchemaBase;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileJsonFgPlus extends ProfileGeoJson {

  private final FeaturesCoreProviders providers;

  @Inject
  ProfileJsonFgPlus(
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      ProfileSetGeoJson profileSet) {
    super(extensionRegistry, profileSet);
    this.providers = providers;
  }

  @Override
  public String getId() {
    return "jsonfg-plus";
  }

  @Override
  public String getLabel() {
    return "JSON-FG+";
  }

  @Override
  public boolean writeJsonFgExtensions() {
    return true;
  }

  @Override
  public boolean writeSecondaryGeometry() {
    return true;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && apiData
            .getExtension(JsonFgConfiguration.class)
            .map(JsonFgConfiguration::getSupportPlusProfile)
            .orElse(false)
        && apiData.getCollections().values().stream()
            .map(cd -> providers.getFeatureSchema(apiData, cd))
            .anyMatch(schema -> schema.flatMap(SchemaBase::getSecondaryGeometry).isPresent());
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && apiData
            .getExtension(JsonFgConfiguration.class, collectionId)
            .map(JsonFgConfiguration::getSupportPlusProfile)
            .orElse(false)
        && apiData
            .getCollectionData(collectionId)
            .flatMap(cd -> providers.getFeatureSchema(apiData, cd))
            .map(schema -> schema.getSecondaryGeometry().isPresent())
            .orElse(false);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return JsonFgConfiguration.class;
  }
}
