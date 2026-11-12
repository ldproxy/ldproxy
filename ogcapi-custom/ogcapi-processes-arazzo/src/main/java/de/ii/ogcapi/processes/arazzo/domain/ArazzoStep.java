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
@JsonDeserialize(builder = ImmutableArazzoStep.Builder.class)
public interface ArazzoStep {

  String getStepId();

  Optional<String> getDescription();

  Optional<String> getOperationId();

  Optional<String> getOperationPath();

  Optional<String> getWorkflowId();

  List<ArazzoParameter> getParameters();

  Optional<ArazzoRequestBody> getRequestBody();

  List<ArazzoCriterion> getSuccessCriteria();

  List<ArazzoAction> getOnSuccess();

  List<ArazzoAction> getOnFailure();

  Map<String, Object> getOutputs();

  // AsyncAPI steps and the step members of version 1.1 that are not supported

  Optional<String> getChannelPath();

  Optional<String> getCorrelationId();

  Optional<String> getAction();

  @Nullable
  Object getTimeout();

  List<String> getDependsOn();

  /** The extension {@code x-dso-forEach}, a loop of a step that calls a workflow. */
  @JsonProperty("x-dso-forEach")
  Optional<ArazzoForEach> getForEach();
}
