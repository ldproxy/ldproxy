/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import jakarta.ws.rs.container.ContainerRequestContext;

@AutoMultiBind
public interface HttpRequestOverrideQueryParameter {

  String getName();

  void applyTo(ContainerRequestContext requestContext, QueryParameterSet parameters);
}
