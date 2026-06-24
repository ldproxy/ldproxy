/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.AbstractPathParameterCollectionId;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title collectionId
 * @endpoints Time Map
 * @langEn The identifier of the feature collection.
 * @langDe Der Identifikator der Feature Collection.
 */
@Singleton
@AutoBind
public class PathParameterCollectionIdVersions extends AbstractPathParameterCollectionId {

  @Inject
  PathParameterCollectionIdVersions(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items/{featureId}/versions".equals(definitionPath);
  }

  @Override
  public String getId() {
    return "collectionIdVersions";
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }
}
