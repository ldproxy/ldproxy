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
import org.immutables.value.Value;

/**
 * A loop over a list, the extension {@code x-dso-forEach} of a step that calls a workflow: the
 * workflow runs once per item, with the item as the input {@code als}. Arazzo has no loops; the
 * extension comes from the Arazzo descriptions of the Digitaal Stelsel Omgevingswet (DSO).
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ImmutableArazzoForEach.Builder.class)
public interface ArazzoForEach {

  /**
   * A runtime expression or a selector object for the list; a single value is a list with one item,
   * no value an empty list.
   */
  Object getItems();

  /** The name of the input of the workflow that receives the item. */
  @JsonProperty("als")
  String getAls();
}
