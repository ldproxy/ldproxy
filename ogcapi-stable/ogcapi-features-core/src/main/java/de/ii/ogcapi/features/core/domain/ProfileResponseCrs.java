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
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

/**
 * A profile that selects the coordinate reference system of the geometries in the response. The CRS
 * is a fallback for the default CRS of the API: it is only used if the request does not include a
 * {@code crs} parameter. It is ignored for HTML, where the {@code crs} parameter is ignored as
 * well.
 */
public interface ProfileResponseCrs {

  /**
   * The CRS of the geometries in the response, or an empty value if the profile does not change the
   * CRS for the collection.
   */
  Optional<EpsgCrs> getResponseCrs(@NotNull OgcApiDataV2 apiData, @NotNull String collectionId);
}
