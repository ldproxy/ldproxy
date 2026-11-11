/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.locationtech.jts.geom.Geometry;

/**
 * A feature as read from {@link FeatureRecordsFormat}: the collection, the id, the values per
 * property path and the primary geometry in the coordinate reference system of the query.
 */
record FeatureRecord(
    String collectionId,
    String featureId,
    Map<String, List<Object>> values,
    Optional<Geometry> geometry) {

  static final String COLLECTION = "collection";
  static final String ID = "id";
  static final String PROPERTIES = "properties";
  static final String GEOMETRY = "geometry";

  /** The member of the only line of a query with hits only, the number of matching features. */
  static final String NUMBER_MATCHED = "numberMatched";

  List<Object> values(String path) {
    return values.getOrDefault(path, List.of());
  }
}
