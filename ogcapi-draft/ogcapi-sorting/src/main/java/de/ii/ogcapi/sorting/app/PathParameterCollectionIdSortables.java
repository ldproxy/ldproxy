/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.sorting.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.AbstractPathParameterCollectionId;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.sorting.domain.SortingConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title collectionId
 * @endpoints Sortables
 * @langEn The identifier of the feature collection.
 * @langDe Der Identifikator der Feature Collection.
 */
@Singleton
@AutoBind
public class PathParameterCollectionIdSortables extends AbstractPathParameterCollectionId {

  @Inject
  public PathParameterCollectionIdSortables(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "collectionIdSortables";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/sortables".equals(definitionPath);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SortingConfiguration.class;
  }
}
