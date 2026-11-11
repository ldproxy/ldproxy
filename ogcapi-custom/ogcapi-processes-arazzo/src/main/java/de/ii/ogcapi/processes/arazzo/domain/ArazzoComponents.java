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
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ImmutableArazzoComponents.Builder.class)
public interface ArazzoComponents {

  /** Reusable JSON Schemas of workflow inputs. */
  Map<String, Object> getInputs();

  Map<String, ArazzoParameter> getParameters();

  Map<String, ArazzoAction> getSuccessActions();

  Map<String, ArazzoAction> getFailureActions();
}
