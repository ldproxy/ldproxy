/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.PermissionGroup;
import de.ii.ogcapi.foundation.domain.PermissionGroup.Base;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.WithProfiles;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

public interface FeaturesCoreQueriesHandler
    extends QueriesHandler<FeaturesCoreQueriesHandler.Query>, Volatile2 {

  String GROUP_DATA = "data";
  PermissionGroup GROUP_DATA_READ =
      PermissionGroup.of(Base.READ, GROUP_DATA, "access and query features");
  PermissionGroup GROUP_DATA_WRITE = PermissionGroup.of(Base.WRITE, GROUP_DATA, "mutate features");
  String BOUNDING_BOX_HEADER = "Content-Bounding-Box";
  String TEMPORAL_EXTENT_HEADER = "Content-Temporal-Extent";

  @Override
  Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers();

  enum Query implements QueryIdentifier {
    FEATURES,
    FEATURE
  }

  interface QueryInputFeaturesBase extends QueryInput, WithProfiles {
    String getCollectionId();

    FeatureQuery getQuery();

    FeatureProvider getFeatureProvider();

    EpsgCrs getDefaultCrs();

    boolean sendResponseAsStream();

    @Value.Default
    default boolean includeBodyLinks() {
      return true;
    }
  }

  @Value.Immutable
  interface QueryInputFeatures extends QueryInputFeaturesBase {
    Optional<Integer> getDefaultPageSize();

    @Value.Default
    @Override
    default boolean sendResponseAsStream() {
      return !getQuery().hitsOnly();
    }

    @Value.Default
    default String getPath() {
      return "/collections/" + getCollectionId() + "/items";
    }
  }

  @Value.Immutable
  interface QueryInputFeature extends QueryInputFeaturesBase {
    String getFeatureId();

    @Value.Default
    @Override
    default boolean sendResponseAsStream() {
      return false;
    }
  }
}
