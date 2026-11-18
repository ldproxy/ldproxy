/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.search.domain.StoredQueryValue;
import de.ii.ogcapi.foundation.domain.ApiDefinitionDependency;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Each stored query has its own resources and parameters in the API definition. */
@Singleton
@AutoBind
public class ApiDefinitionDependencyStoredQueries implements ApiDefinitionDependency {

  private final Values<StoredQueryValue> storedQueries;

  @Inject
  public ApiDefinitionDependencyStoredQueries(ValueStore valueStore) {
    this.storedQueries = valueStore.forType(StoredQueryValue.class);
  }

  @Override
  public long getRevision() {
    return storedQueries.getRevision();
  }
}
