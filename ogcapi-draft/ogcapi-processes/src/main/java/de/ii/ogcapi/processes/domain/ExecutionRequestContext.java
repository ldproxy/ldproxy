/*
 * Copyright 2024 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.immutables.value.Value;

/**
 * The context of the request that executes a process. The job of the execution carries it to its
 * steps, so that a step answers as a request of the API with this context would; for now the
 * requested profiles, e.g. how a step that returns features encodes their references.
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDeserialize(builder = ImmutableExecutionRequestContext.Builder.class)
public interface ExecutionRequestContext {

  /** The member of the context of the job that holds the request context. */
  String JOB_CONTEXT_MEMBER = "requestContext";

  ExecutionRequestContext NONE = new ImmutableExecutionRequestContext.Builder().build();

  /** The ids of the requested profiles. */
  List<String> getProfiles();

  static ExecutionRequestContext ofProfiles(List<String> profiles) {
    return new ImmutableExecutionRequestContext.Builder().profiles(profiles).build();
  }

  /**
   * @param jobContext the context of a job
   * @return the request context of the execution that created the job, none if it has none
   */
  static ExecutionRequestContext from(Map<String, Object> jobContext, ObjectMapper mapper) {
    Object context = jobContext.get(JOB_CONTEXT_MEMBER);
    if (Objects.isNull(context)) {
      return NONE;
    }
    return mapper.convertValue(context, ExecutionRequestContext.class);
  }
}
