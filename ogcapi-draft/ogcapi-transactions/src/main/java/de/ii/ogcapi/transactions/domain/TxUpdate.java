/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import com.fasterxml.jackson.databind.JsonNode;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Partial update action. {@link #getAdd()}, {@link #getModify()} and {@link #getDeleteProperties()}
 * are translated by the executor into a single GeoJSON merge-patch document (deleted properties
 * become {@link de.ii.xtraplatform.features.domain.FeatureTransactions#PATCH_NULL_VALUE}) that is
 * applied via the partial-update path on the feature provider session.
 */
@Value.Immutable
public interface TxUpdate extends TxAction {

  @Override
  default TxActionType getType() {
    return TxActionType.UPDATE;
  }

  /** Property name/value pairs to set when not already present. */
  List<NameValue> getAdd();

  /** Property name/value pairs to overwrite. */
  List<NameValue> getModify();

  /** Property paths whose value is to be cleared. */
  List<List<String>> getDeleteProperties();

  Optional<Cql2Expression> getFilter();

  Optional<EpsgCrs> getFilterCrs();

  @Value.Immutable
  interface NameValue {
    // Pre-resolution path: one or more segments parsed from `<wfs:ValueReference>` or the JSON
    // body. Segments are local names (any XML namespace prefix has been stripped). Intermediate
    // object-type element steps (e.g. `AA_Lebenszeitintervall` between `lebenszeitintervall` and
    // `endet`) are preserved; the executor validates them against the schema. The form of the
    // property segments (schema id vs alias) is governed by the input format's configuration
    // (GmlConfiguration.useAlias for wfs:Transaction).
    List<String> getPath();

    JsonNode getValue();
  }
}
