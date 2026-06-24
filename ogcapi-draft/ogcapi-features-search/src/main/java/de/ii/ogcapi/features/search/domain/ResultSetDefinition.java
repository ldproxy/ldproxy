/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Definition of a named result set in a query. An empty definition declares a result set that
 * consists of the ids of the features selected by the query. If {@code values} is set, the result
 * set consists of the ids referenced by that property of the selected features (projected result
 * set).
 */
@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableResultSetDefinition.Builder.class)
public interface ResultSetDefinition {

  Optional<String> getValues();
}
