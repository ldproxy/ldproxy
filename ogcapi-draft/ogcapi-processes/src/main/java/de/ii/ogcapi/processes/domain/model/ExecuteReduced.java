/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableExecuteReduced.Builder.class)
public interface ExecuteReduced {

  Optional<String> getProcess();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, Object> getInputs();

  // Optional must be used to distinguish between empty and omitted Output
  Optional<Map<String, String>> getOutputs();
}
