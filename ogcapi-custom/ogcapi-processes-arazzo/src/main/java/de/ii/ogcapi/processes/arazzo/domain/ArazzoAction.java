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
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * A success or failure action, or a reusable object that refers to an action of the components with
 * {@code reference}.
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ImmutableArazzoAction.Builder.class)
public interface ArazzoAction {

  String END = "end";
  String GOTO = "goto";
  String RETRY = "retry";

  Optional<String> getName();

  Optional<String> getType();

  Optional<String> getWorkflowId();

  Optional<String> getStepId();

  Optional<Double> getRetryAfter();

  Optional<Integer> getRetryLimit();

  List<ArazzoCriterion> getCriteria();

  Optional<String> getReference();

  // the inputs of a workflow that is the target of the action, not supported
  List<Object> getParameters();
}
