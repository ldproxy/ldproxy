/*
 * Copyright 2024 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.processes.domain.model.OgcExecute;
import de.ii.ogcapi.processes.domain.model.OgcProcess;
import de.ii.xtralink.jobs.JobConfiguration;
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity;
import de.ii.xtraplatform.xtralink.domain.JobInputs;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableOapJob.Builder.class)
public interface OapJob extends JobInputs {

  String KIND = "oap";
  String LABEL = "OGC API Processes";

  static String kind(String... parts) {
    return String.join(":", KIND, String.join(":", parts));
  }

  static String processId(String jobKind) {
    if (!jobKind.startsWith(KIND + ":")) {
      throw new IllegalArgumentException("Invalid job kind for OapJob: " + jobKind);
    }
    return jobKind.substring(KIND.length() + 1);
  }

  static JobConfiguration of(String apiId, OgcProcess process, OgcExecute executeRequest) {
    ImmutableOapJob oapJob =
        new ImmutableOapJob.Builder().inputs(executeRequest.getInputs()).build();
    return Jobs.create(
        kind(process.getId()),
        1000,
        LABEL,
        String.format(" (Process: %s)", process.getTitle()),
        oapJob,
        new OapJobContext(apiId, executeRequest),
        null);
  }

  class OapJobContext extends JobContextEntity {
    private final OgcExecute executeRequest;

    public OapJobContext(String entity, OgcExecute executeRequest) {
      super(entity);
      this.executeRequest = executeRequest;
    }

    public OgcExecute getExecuteRequest() {
      return executeRequest;
    }
  }

  @JsonAnyGetter
  @JsonAnySetter
  Map<String, Object> getInputs();
}
