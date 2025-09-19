/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import de.ii.ogcapi.foundation.domain.PermissionGroup;
import de.ii.ogcapi.foundation.domain.PermissionGroup.Base;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import java.util.Optional;
import org.immutables.value.Value;

public interface QueriesHandlerStyles
    extends QueriesHandler<QueriesHandlerStyles.Query>, Volatile2 {

  String GROUP_STYLES = "styles";
  PermissionGroup GROUP_STYLES_READ =
      PermissionGroup.of(Base.READ, GROUP_STYLES, "access styles and their metadata");
  PermissionGroup GROUP_STYLES_WRITE =
      PermissionGroup.of(Base.WRITE, GROUP_STYLES, "mutate styles and update their metadata");

  enum Query implements QueryIdentifier {
    STYLES,
    STYLE,
    STYLE_METADATA,
    STYLE_LEGEND
  }

  @Value.Immutable
  interface QueryInputStyles extends QueryInput {
    Optional<String> getCollectionId();

    boolean getIncludeLinkHeader();
  }

  @Value.Immutable
  interface QueryInputStyle extends QueryInput {
    Optional<String> getCollectionId();

    String getStyleId();

    Optional<TileMatrixSet> getTileMatrixSet();

    boolean getIncludeLinkHeader();
  }
}
