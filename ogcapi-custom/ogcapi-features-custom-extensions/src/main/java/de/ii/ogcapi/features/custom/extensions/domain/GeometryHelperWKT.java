/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.custom.extensions.domain;

import com.fasterxml.jackson.databind.JsonNode;

public interface GeometryHelperWKT {

  // TODO: centralize WKT handling

  String getPointRegex();

  String getMultiPointRegex();

  String getLineStringRegex();

  String getMultiLineStringRegex();

  String getPolygonRegex();

  String getMultiPolygonRegex();

  String convertGeometryToWkt(Geometry geom);

  String convertGeoJsonToWkt(JsonNode geom);

  Geometry extractGeometry(String text);
}
