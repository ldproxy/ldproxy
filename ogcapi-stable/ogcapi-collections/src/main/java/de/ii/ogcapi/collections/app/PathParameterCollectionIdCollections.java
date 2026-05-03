/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.AbstractPathParameterCollectionId;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title collectionId
 * @endpoints Feature Collection
 * @langEn The identifier of the feature collection.
 * @langDe Der Identifikator der Feature Collection.
 */
@Singleton
@AutoBind
public class PathParameterCollectionIdCollections extends AbstractPathParameterCollectionId {

  @Inject
  public PathParameterCollectionIdCollections(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}".equals(definitionPath);
  }

  @Override
  public String getId() {
    return "collectionIdCollections";
  }
}
