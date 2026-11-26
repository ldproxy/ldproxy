/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiDefinitionDependency;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Codelist values are part of property schemas, for example of query parameters. */
@Singleton
@AutoBind
public class ApiDefinitionDependencyCodelists implements ApiDefinitionDependency {

  private final Values<Codelist> codelists;

  @Inject
  public ApiDefinitionDependencyCodelists(ValueStore valueStore) {
    this.codelists = valueStore.forType(Codelist.class);
  }

  @Override
  public long getRevision() {
    return codelists.getRevision();
  }
}
