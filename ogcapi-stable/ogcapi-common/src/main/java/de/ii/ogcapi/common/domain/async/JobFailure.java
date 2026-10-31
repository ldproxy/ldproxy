/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * The reason for the failure of a job, stated by the processor that knows it. The result of the
 * failed job at {@code /jobs/{jobId}/results} is then a problem with this status, type and detail
 * (Requirement 60 of OGC API Processes: the status corresponds to the reason for the failure).
 * Without a stated reason, the failure is unexpected and the result has the status 500.
 *
 * <p>A processor sets the reason as the job outputs, or adds it to them, before it reports the
 * failure. Job outputs are persisted as plain maps, so the reason is a map below the key {@code
 * failure}.
 */
@Value.Immutable
public interface JobFailure {

  String KEY = "failure";

  static JobFailure of(int status, String detail) {
    return new ImmutableJobFailure.Builder().status(status).detail(detail).build();
  }

  static JobFailure of(int status, String type, String detail) {
    return new ImmutableJobFailure.Builder().status(status).type(type).detail(detail).build();
  }

  static Optional<JobFailure> from(Map<String, Object> outputs) {
    if (!(outputs.get(KEY) instanceof Map<?, ?> failure)
        || !(failure.get("status") instanceof Number status)
        || !(failure.get("detail") instanceof String detail)) {
      return Optional.empty();
    }
    return Optional.of(
        new ImmutableJobFailure.Builder()
            .status(status.intValue())
            .type(Optional.ofNullable(failure.get("type")).map(Object::toString))
            .detail(detail)
            .build());
  }

  /** The HTTP status that corresponds to the reason, e.g. 422. */
  int getStatus();

  /** The exception type, a URI; without it the type of the problem is "about:blank". */
  Optional<String> getType();

  String getDetail();

  /** The reason as job outputs. */
  default Map<String, Object> asOutputs() {
    return Map.of(KEY, asMap());
  }

  /** The given job outputs with the reason added. */
  default Map<String, Object> addTo(Map<String, Object> outputs) {
    Map<String, Object> merged = new LinkedHashMap<>(outputs);
    merged.put(KEY, asMap());
    return merged;
  }

  private Map<String, Object> asMap() {
    Map<String, Object> failure = new LinkedHashMap<>();
    failure.put("status", getStatus());
    getType().ifPresent(type -> failure.put("type", type));
    failure.put("detail", getDetail());
    return failure;
  }
}
