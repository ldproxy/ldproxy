/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.xtraplatform.features.domain.Tuple;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Optional;

/**
 * Extension hook for resources that add response metadata to a successful single-feature lookup.
 * After {@code GET /collections/{collectionId}/items/{featureId}} returned a feature, the queries
 * handler passes the response builder to every implementation that is enabled for the collection,
 * e.g. to add headers that depend on the temporal extent of the returned feature. Without an
 * enabled implementation the response is not changed.
 */
@AutoMultiBind
public interface SingleFeatureResponseCustomizer extends ApiExtension {

  /**
   * @param temporalExtent the start and end of the primary temporal value of the feature, each may
   *     be {@code null}
   */
  void customize(
      Response.ResponseBuilder response,
      OgcApi api,
      String collectionId,
      String featureId,
      Optional<Tuple<Instant, Instant>> temporalExtent,
      ApiRequestContext requestContext);
}
