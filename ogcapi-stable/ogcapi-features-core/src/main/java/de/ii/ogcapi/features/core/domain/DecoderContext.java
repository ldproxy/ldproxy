/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.geometries.domain.Axes;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Inputs to {@link FeatureFormatExtension#getFeatureDecoder(DecoderContext)}. Carries everything a
 * format needs to construct a token decoder for an incoming request body: the target schema, the
 * CRS in which the body is to be interpreted (resolved from the {@code OGC-Content-Crs} request
 * header or, when absent, the collection's default CRS), the axes, the parsed request media type
 * (so format parameters such as {@code version} or {@code profile} are visible), the API/collection
 * identity for looking up per-collection configuration, and the coordinate reference systems that
 * the collection supports (a body must not use another one).
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
public interface DecoderContext {

  OgcApiDataV2 getApiData();

  String getCollectionId();

  FeatureSchema getFeatureSchema();

  EpsgCrs getCrs();

  Axes getAxes();

  MediaType getMediaType();

  /**
   * The coordinate reference systems that the collection supports. A geometry in a request body
   * that declares another one (a JSON-FG {@code coordRefSys} member, a GML {@code srsName}
   * attribute) is rejected. An empty list does not restrict the coordinate reference systems.
   */
  @Value.Default
  default List<EpsgCrs> getSupportedCrs() {
    return List.of();
  }

  /**
   * The properties that the published schema specifies as read-only, see {@link
   * ReadOnlyProperties}. A request body that sets one of them is rejected. An empty set does not
   * restrict the properties, which is what a body that was merged with the current feature needs:
   * it carries the read-only properties of that feature whether or not the client sent them.
   */
  @Value.Default
  default Set<String> getReadOnlyProperties() {
    return Set.of();
  }
}
