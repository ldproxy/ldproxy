/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import jakarta.ws.rs.core.Response;

public interface PolicyAttributeFeaturesGetter {
  Response getItems(ApiRequestContext requestContext, String collectionId);
}
