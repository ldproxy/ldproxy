/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "processes")
@JsonInclude(Include.NON_EMPTY)
@JsonDeserialize(builder = ImmutableProcessDescriptionData.Builder.class)
public interface ProcessDescriptionData extends StoredValue {

  String getId();

  String getTitle();

  String getDescription();

  String getVersion();

  JOB_CONTROL_OPTIONS getJobControlOptions();

  enum JOB_CONTROL_OPTIONS {
    SYNC_EXECUTE,
    ASYNC_EXECUTE,
    DISMISS
  }

  abstract class Builder implements ValueBuilder<ProcessDescriptionData> {}
}
