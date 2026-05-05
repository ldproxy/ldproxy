/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.queryables.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.AbstractPathParameterCollectionId;
import de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title collectionId
 * @endpoints Queryables
 * @langEn The identifier of the feature collection.
 * @langDe Der Identifikator der Feature Collection.
 */
@Singleton
@AutoBind
public class PathParameterCollectionIdQueryables extends AbstractPathParameterCollectionId {

  @Inject
  PathParameterCollectionIdQueryables(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "collectionIdQueryables";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/queryables".equals(definitionPath);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return QueryablesConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData.getCollections().keySet().stream()
        .anyMatch(collectionId -> isEnabledForApi(apiData, collectionId));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getExtension(QueryablesConfiguration.class, collectionId)
        .filter(QueryablesConfiguration::isEnabled)
        .filter(QueryablesConfiguration::endpointIsEnabled)
        .isPresent();
  }
}
