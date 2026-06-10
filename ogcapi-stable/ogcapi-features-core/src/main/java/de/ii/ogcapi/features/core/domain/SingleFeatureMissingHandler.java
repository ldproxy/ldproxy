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
import jakarta.ws.rs.core.Response;
import java.util.Optional;

/**
 * Extension hook for resources that affect how a missing single-feature lookup is reported. When
 * {@code GET /collections/{collectionId}/items/{featureId}} returned zero features, the queries
 * handler asks every registered implementation for an alternative response (e.g. {@code 410 Gone}
 * for a feature whose only versions have all been retired). The first non-empty {@link Response}
 * wins; if every implementation declines, the default behaviour of {@code 404 Not Found} applies.
 */
@AutoMultiBind
public interface SingleFeatureMissingHandler extends ApiExtension {

  Optional<Response> handleMissing(
      OgcApi api, String collectionId, String featureId, ApiRequestContext requestContext);
}
