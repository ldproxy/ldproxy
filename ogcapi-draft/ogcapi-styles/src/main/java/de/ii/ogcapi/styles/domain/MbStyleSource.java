/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MbStyleVectorSource.class, name = "vector"),
  @JsonSubTypes.Type(value = MbStyleRasterSource.class, name = "raster"),
  @JsonSubTypes.Type(value = MbStyleRasterDemSource.class, name = "raster-dem"),
  @JsonSubTypes.Type(value = MbStyleGeojsonSource.class, name = "geojson"),
  @JsonSubTypes.Type(value = MbStyleImageSource.class, name = "image"),
  @JsonSubTypes.Type(value = MbStyleVideoSource.class, name = "video")
})
public interface MbStyleSource {
  enum Scheme {
    xyz,
    tms
  }
}
