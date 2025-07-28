/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain;

import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.WithProfiles;
import de.ii.ogcapi.tiles.domain.TilesConfiguration.WmtsScope;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.tiles.domain.TileCoordinates;
import de.ii.xtraplatform.tiles.domain.TilesFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

public interface TilesQueriesHandler extends QueriesHandler<TilesQueriesHandler.Query>, Volatile2 {

  @Override
  Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers();

  enum Query implements QueryIdentifier {
    TILE_SETS,
    TILE_SET,
    TILE,
    WMTS
  }

  @Value.Immutable
  interface QueryInputTile extends QueryInput, TileCoordinates {

    Optional<String> getCollectionId();

    Optional<String> getStyleId();

    TileFormatExtension getOutputFormat();

    QueryParameterSet getParameters();
  }

  @Value.Immutable
  interface QueryInputTileSets extends QueryInput, WithProfiles {

    Optional<String> getCollectionId();

    Optional<String> getStyleId();

    List<String> getTileMatrixSetIds();

    String getPath();

    boolean getOnlyWebMercatorQuad();

    List<TilesFormat> getTileEncodings();
  }

  @Value.Immutable
  interface QueryInputTileSet extends QueryInput, WithProfiles {

    Optional<String> getCollectionId();

    Optional<String> getStyleId();

    String getTileMatrixSetId();

    String getPath();
  }

  @Value.Immutable
  interface QueryInputWmts extends QueryInput {

    WmtsScope getScope();
  }
}
