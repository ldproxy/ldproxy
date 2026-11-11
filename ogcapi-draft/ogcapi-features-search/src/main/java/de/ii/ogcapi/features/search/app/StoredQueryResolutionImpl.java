/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.features.search.domain.StoredQueryResolution;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.cql.domain.Cql;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
@AutoBind
public class StoredQueryResolutionImpl implements StoredQueryResolution {

  private final StoredQueryResolver resolver;

  @Inject
  StoredQueryResolutionImpl(
      ExtensionRegistry extensionRegistry,
      StoredQueryRepository repository,
      SchemaValidator schemaValidator,
      Cql cql) {
    this.resolver = new StoredQueryResolver(extensionRegistry, repository, schemaValidator, cql);
  }

  @Override
  public Resolved resolve(OgcApi api, String queryId, Map<String, String> parameters) {
    return resolver.resolveParameters(api, queryId, parameters);
  }
}
