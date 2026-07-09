/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableExecuteResponseBodyDummy.Builder.class)
public interface ExecuteResponseBodyDummy {

  String SCHEMA_REF = "#/components/schemas/ExecuteResponseBody";

  ProcessesExecutor.StatusCode getStatus();

  Optional<String> getJobId();

  Optional<String> getOutput();
}
