/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Definition of a named result set in a query. An empty definition declares a result set that
 * consists of the ids of the features selected by the query. If {@code values} is set, the result
 * set consists of the ids referenced by that property of the selected features (projected result
 * set). Both are consumed with {@code inResultSet}.
 *
 * <p>If {@code key} is set, the result set consists of composite keys: one member per key part,
 * mapping the name of the part to the property of this feature type that holds it. Such a set is
 * consumed with {@code inResultSetByKey}, whose key uses the same part names. The names are
 * arbitrary but must be the same on both sides, so that the parts are matched by name rather than
 * by position — with a key whose parts all have the same type, a positional match could transpose
 * two of them without anything noticing.
 */
@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableResultSetDefinition.Builder.class)
public interface ResultSetDefinition {

  Optional<String> getValues();

  Map<String, String> getKey();

  @Value.Check
  default void check() {
    Preconditions.checkState(
        getValues().isEmpty() || getKey().isEmpty(),
        "A result set is either a set of values ('values') or a set of composite keys ('key'), not both.");
  }
}
