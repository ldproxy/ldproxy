/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.ogc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

// ToDo use correct types
@ApiInfo(schemaId = "Execute")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcExecute.Builder.class)
public interface OgcExecute {

  String SCHEMA_REF = "#/components/schemas/Execute";

  @JsonProperty("process")
  Optional<String> getProcessUri();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  // Map<String, Values> getInputs();
  Map<String, Object> getInputs();

  // TODO Use Optional to distinguish between empty Output and no Output
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  // Map<String, OutputSelection> getOutputs();
  Map<String, String> getOutputs();

  // Optional<Subscriber> getSubscriber();
  Optional<String> getSubscriber();
}
