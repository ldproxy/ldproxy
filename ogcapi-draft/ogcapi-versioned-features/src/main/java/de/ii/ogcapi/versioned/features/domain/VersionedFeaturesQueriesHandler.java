/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import java.util.Map;
import org.immutables.value.Value;

public interface VersionedFeaturesQueriesHandler
    extends QueriesHandler<VersionedFeaturesQueriesHandler.Query>, Volatile2 {

  @Override
  Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers();

  enum Query implements QueryIdentifier {
    TIME_MAP
  }

  @Value.Immutable
  interface QueryInputTimeMap extends QueryInput {
    String getCollectionId();

    String getFeatureId();

    FeatureProvider getFeatureProvider();
  }
}
