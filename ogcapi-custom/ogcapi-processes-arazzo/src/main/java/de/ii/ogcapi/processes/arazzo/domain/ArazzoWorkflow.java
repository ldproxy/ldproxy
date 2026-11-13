/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ImmutableArazzoWorkflow.Builder.class)
public interface ArazzoWorkflow {

  String getWorkflowId();

  Optional<String> getSummary();

  Optional<String> getDescription();

  /** The JSON Schema of the inputs, an object schema, or a reference to a reusable schema. */
  @Nullable
  Object getInputs();

  // not supported
  List<String> getDependsOn();

  List<ArazzoStep> getSteps();

  List<ArazzoAction> getSuccessActions();

  List<ArazzoAction> getFailureActions();

  /** The outputs by name: a runtime expression, or a selector object, which is not supported. */
  Map<String, Object> getOutputs();

  List<ArazzoParameter> getParameters();

  /**
   * The extension {@code x-ldproxy-readOnly}: whether the workflow only reads data and has no other
   * effects, also when a step uses POST.
   */
  @JsonProperty("x-ldproxy-readOnly")
  @Value.Default
  default boolean isReadOnly() {
    return false;
  }
}
