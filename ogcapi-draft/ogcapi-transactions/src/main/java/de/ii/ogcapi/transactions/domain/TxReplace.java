/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Replace action. {@link #getFeature()} is the raw payload of a single feature (typically a GeoJSON
 * Feature). Exactly one of {@link #getFilter()} or a feature identifier carried inside the feature
 * payload is expected; if {@link #getFilter()} is present, the executor resolves it to one or more
 * feature ids before applying the replace.
 */
@Value.Immutable
public interface TxReplace extends TxAction {

  @Override
  default TxActionType getType() {
    return TxActionType.REPLACE;
  }

  byte[] getFeature();

  @Value.Default
  default MediaType getMediaType() {
    return MediaType.valueOf("application/geo+json");
  }

  // See TxDelete.getTargetIds — same WFS-direct vs JSON-tx-CQL2 split.
  List<String> getTargetIds();

  Optional<Cql2Expression> getFilter();

  Optional<EpsgCrs> getFilterCrs();
}
