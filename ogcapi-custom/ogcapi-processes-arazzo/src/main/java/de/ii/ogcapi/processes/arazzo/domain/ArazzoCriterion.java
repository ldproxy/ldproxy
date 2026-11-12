/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ImmutableArazzoCriterion.Builder.class)
public interface ArazzoCriterion {

  String SIMPLE = "simple";
  String REGEX = "regex";
  String JSONPATH = "jsonpath";
  String XPATH = "xpath";

  Optional<String> getContext();

  String getCondition();

  /** The type, a string or an expression type object with {@code type} and {@code version}. */
  @Nullable
  Object getType();
}
