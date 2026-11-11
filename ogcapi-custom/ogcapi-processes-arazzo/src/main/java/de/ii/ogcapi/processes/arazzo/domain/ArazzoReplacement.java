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
import javax.annotation.Nullable;
import org.immutables.value.Value;

/** A payload replacement: a JSON Pointer into the payload and the value to put there. */
@Value.Immutable
@Value.Style(builder = "new")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ImmutableArazzoReplacement.Builder.class)
public interface ArazzoReplacement {

  String getTarget();

  @Nullable
  Object getValue();

  // only JSON Pointer targets are supported
  @Nullable
  Object getTargetSelectorType();
}
