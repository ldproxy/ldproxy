/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.domain;

import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import java.util.Optional;
import org.immutables.value.Value;

public interface QueriesHandler3dTiles extends QueriesHandler<QueriesHandler3dTiles.Query> {

  enum Query implements QueryIdentifier {
    TILESET,
    FILE
  }

  @Value.Immutable
  interface QueryInputTileset extends QueryInput {
    Optional<String> getCollectionId();
  }

  @Value.Immutable
  interface QueryInputFile extends QueryInput {
    Optional<String> getCollectionId();

    String getPath();
  }
}
