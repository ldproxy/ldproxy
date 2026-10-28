/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * The result of a job that is a single document in the job store instead of a set of named outputs.
 * It is set as the job outputs and served as is at {@code /jobs/{jobId}/results}.
 *
 * <p>Job outputs are persisted as plain maps, so a stored reference is recognized by its keys.
 *
 * <p>A job that failed can also have a result document, e.g. the response document of a rejected
 * transaction; it is served with the HTTP status of the failure.
 */
@Value.Immutable
public interface JobResultFile {

  static JobResultFile of(String resultPath, String resultMediaType) {
    return ImmutableJobResultFile.of(resultPath, resultMediaType);
  }

  static JobResultFile of(String resultPath, String resultMediaType, int resultStatus) {
    return ImmutableJobResultFile.of(resultPath, resultMediaType).withResultStatus(resultStatus);
  }

  static boolean is(Map<String, Object> outputs) {
    return outputs.get("resultPath") instanceof String
        && outputs.get("resultMediaType") instanceof String;
  }

  static Optional<JobResultFile> from(Map<String, Object> outputs) {
    if (!is(outputs)) {
      return Optional.empty();
    }
    JobResultFile resultFile =
        of((String) outputs.get("resultPath"), (String) outputs.get("resultMediaType"));
    if (outputs.get("resultStatus") instanceof Number status) {
      resultFile = ImmutableJobResultFile.copyOf(resultFile).withResultStatus(status.intValue());
    }
    return Optional.of(resultFile);
  }

  /** The path of the document, relative to the store of the job kind. */
  @Value.Parameter
  String getResultPath();

  @Value.Parameter
  String getResultMediaType();

  /** The HTTP status of the document, if it is not 200. */
  @Nullable
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer getResultStatus();

  default int status() {
    return Optional.ofNullable(getResultStatus()).orElse(200);
  }

  default Path path() {
    return Path.of(getResultPath());
  }
}
