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
import java.util.Optional;
import org.immutables.value.Value;

/** A source description: an OpenAPI document or another Arazzo description. */
@Value.Immutable
@Value.Style(builder = "new")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ImmutableArazzoSource.Builder.class)
public interface ArazzoSource {

  String OPENAPI = "openapi";
  String ARAZZO = "arazzo";

  String getName();

  String getUrl();

  Optional<String> getType();
}
