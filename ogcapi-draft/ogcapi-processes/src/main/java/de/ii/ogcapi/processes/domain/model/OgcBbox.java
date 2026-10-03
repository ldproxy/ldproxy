/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.util.List;
import org.immutables.value.Value;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/bbox.yaml
 */
@ApiInfo(schemaId = "Bbox")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcBbox.Builder.class)
public interface OgcBbox {

  String SCHEMA_REF = "#/components/schemas/Bbox";

  enum CRS {
    @JsonProperty("CRS84")
    @JsonAlias("http://www.opengis.net/def/crs/OGC/1.3/CRS84")
    CRS84,
    @JsonProperty("CRS84h")
    @JsonAlias("http://www.opengis.net/def/crs/OGC/0/CRS84h")
    CRS84h
  }

  List<Double> getBbox();

  @Value.Default
  default CRS getCrs() {
    return CRS.CRS84;
  }
}
