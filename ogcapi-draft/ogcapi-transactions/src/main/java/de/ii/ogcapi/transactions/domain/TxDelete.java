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
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface TxDelete extends TxAction {

  @Override
  default TxActionType getType() {
    return TxActionType.DELETE;
  }

  // wfs:Transaction's fes:ResourceId yields a known list of ids directly; the executor uses
  // these without going through the filter expression. Empty for JSON-tx where the filter is
  // an arbitrary CQL2 id predicate.
  List<String> getTargetIds();

  Optional<Cql2Expression> getFilter();

  Optional<EpsgCrs> getFilterCrs();
}
