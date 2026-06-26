/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "processes")
@JsonInclude(Include.NON_EMPTY)
@JsonDeserialize(builder = ImmutableProcessDescriptionData.Builder.class)
public interface ProcessDescriptionData extends StoredValue {

  enum JOB_CONTROL_OPTIONS {
    SYNC_EXECUTE,
    ASYNC_EXECUTE,
    DISMISS
  }

  String getId();

  String getVersion();

  Optional<String> getTitle();

  Optional<String> getDescription();

  Optional<List<JOB_CONTROL_OPTIONS>> getJobControlOptions();

  Optional<List<String>> getKeywords();

  abstract class Builder implements ValueBuilder<ProcessDescriptionData> {}
}
