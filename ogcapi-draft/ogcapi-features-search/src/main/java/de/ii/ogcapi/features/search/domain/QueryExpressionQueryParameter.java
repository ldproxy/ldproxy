/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;

@AutoMultiBind
public interface QueryExpressionQueryParameter {

  void applyTo(ImmutableStoredQueryExpression.Builder builder, QueryParameterSet queryParameterSet);
}
