/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcSchema.Builder.class)
public interface OgcSchema {

  enum Format {
    @JsonProperty("ogc-bbox")
    @JsonAlias("https://www.opengis.net/def/format/ogcapi-processes/0/ogc-bbox")
    OGC_BBOX
  }

  PropertySchema.Type getType();

  // Note: This is an addition to the draft
  Optional<Format> getFormat();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getRequired();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, PropertySchema> getProperties();
}
